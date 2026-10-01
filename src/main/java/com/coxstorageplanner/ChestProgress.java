package com.coxstorageplanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A chest's plan checked against the inventory right now, in three phases: gear to wear first, then
 * deposits, then withdrawals. A deposit is done when none of the item is left, or, for a line with a
 * number, when that many went in since the storage was opened or are already in it; "everything else"
 * is done when nothing is carried that the take-out list doesn't keep. A withdrawal is done when the
 * inventory and worn equipment together hold the number asked for; the same item on a later line asks
 * for that many more; one whose item is nowhere, not on you and not in the storage, is skipped. A "wear"
 * line is done once it's worn. Potions light up fullest first. Numbers are quantities, so a stack of 14
 * juice counts as 14. Containers are maps of item name to quantity; a null storage is one the client
 * hasn't seen, so nothing is skipped for not being in it.
 *
 * <p>With putBack, an ordered plan is held to the slot: given the inventory slot by slot ({@link Carried})
 * it goes by {@link ChestLayout}, where a withdrawal is done when its slot holds it and anything in
 * the wrong slot goes back in. Without the slots it only asks back what belongs to a later step.
 *
 * <p>A storage that's full turns the phases into rounds: put in what fits, take out, put in the rest.
 */
public final class ChestProgress
{
	public static final class Step
	{
		public final ChestPlan.Line line;
		public final boolean deposit;
		public final boolean done;
		/** Position in the withdraw list, 1-based, 0 for deposits. */
		public final int order;
		/** A withdrawal skipped because the item is neither on you nor in the storage; done as well. */
		public final boolean missing;
		/** How many more still to move, so only that many light up; MAX_VALUE for all of them. */
		public final int remaining;
		/**
		 * Withdrawals: the number of this step's next click, counting every click of the plan from 1.
		 * "Xeric's aid, 2" is two clicks, a stack is one, and a skipped step is none.
		 */
		public final int click;
		/** Withdrawals: clicks still to make before this step's next one, 0 for the very next click. */
		public final int rank;
		/** Withdrawals: whether it comes out as one stack, so one click whatever the count. */
		public final boolean stack;

		Step(ChestPlan.Line line, boolean deposit, boolean done, int order, int remaining)
		{
			this(line, deposit, done, order, false, remaining, 0, 0, false);
		}

		Step(ChestPlan.Line line, boolean deposit, boolean done, int order, boolean missing, int remaining,
			int click, int rank, boolean stack)
		{
			this.line = line;
			this.deposit = deposit;
			this.done = done;
			this.order = order;
			this.missing = missing;
			this.remaining = done ? 0 : remaining;
			this.click = click;
			this.rank = rank;
			this.stack = stack;
		}
	}

	public enum Phase
	{
		WEAR, DEPOSIT, WITHDRAW
	}

	/** The inventory slot by slot, and how the storage stands. */
	public static final class Carried
	{
		/** Item names by inventory slot, null for an empty one. */
		final String[] names;
		final int[] quantities;
		/** Storage slots left, negative when that isn't known. */
		final int free;
		/** Whether the last look at this storage was in a round of withdrawals, so the round gets finished. */
		final boolean withdrawing;

		public Carried(String[] names, int[] quantities, int free, boolean withdrawing)
		{
			this.names = names;
			this.quantities = quantities;
			this.free = free;
			this.withdrawing = withdrawing;
		}

		public Carried(List<String> names)
		{
			this(names.toArray(new String[0]), ones(names.size()), -1, false);
		}

		public Carried with(int free, boolean withdrawing)
		{
			return new Carried(names, quantities, free, withdrawing);
		}

		private static int[] ones(int n)
		{
			int[] ones = new int[n];
			java.util.Arrays.fill(ones, 1);
			return ones;
		}
	}

	/** A withdrawal that can be made right now, by slot: its step and its number in the plan. */
	public static final class Click
	{
		public final Step step;
		public final int number;

		private Click(Step step, int number)
		{
			this.step = step;
			this.number = number;
		}
	}

	public final ChestPlan plan;
	/** Gear to put on, before anything else. */
	public final List<Step> wears;
	public final List<Step> deposits;
	public final List<Step> withdrawals;
	/** Carried items to put back before taking out: in the wrong slot, or without the slots, of a later step. */
	public final List<String> outOfOrder;
	/** Clicks the whole take-out list comes to, skipped steps left out. */
	public final int clicks;
	/** Storage slots left, negative when that isn't known. */
	public final int free;
	/** Things still have to go in, but the storage is full and nothing can come out to make room. */
	public final boolean blocked;
	/** Whether a round of withdrawals is on, to hand back in with the next {@link Carried}. */
	public final boolean withdrawing;
	/** What the storage holds, null when the client hasn't seen it. */
	private final Map<String, Integer> storage;
	private final List<ChestPlan.Line> takeOut;
	private final Phase phase;
	/** The plan slot by slot, null when it goes by counts. */
	private final ChestLayout layout;
	private final Carried carried;
	/** With a layout, the withdrawals that can be made now, in click order. */
	private final List<Click> queue;

	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory)
	{
		this(plan, inventory, Collections.emptyMap(), inventory, null);
	}

	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory, Map<String, Integer> openedWith, Map<String, Integer> storage)
	{
		this(plan, inventory, Collections.emptyMap(), openedWith, storage);
	}

	/**
	 * @param worn what's equipped, which counts as withdrawn too so gear you put on stays ticked off
	 * @param openedWith the inventory when the storage was opened
	 * @param storage what the storage holds now, null if the client hasn't seen it
	 */
	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory, Map<String, Integer> worn,
		Map<String, Integer> openedWith, Map<String, Integer> storage)
	{
		this(plan, inventory, worn, openedWith, storage, false);
	}

	/** @param putBack whether an ordered plan wants carried items of later steps put back first */
	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory, Map<String, Integer> worn,
		Map<String, Integer> openedWith, Map<String, Integer> storage, boolean putBack)
	{
		this(plan, inventory, worn, openedWith, storage, putBack, Collections.emptySet());
	}

	/** @param stackable names of the items that stack, which come out in one click whatever the count */
	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory, Map<String, Integer> worn,
		Map<String, Integer> openedWith, Map<String, Integer> storage, boolean putBack, Set<String> stackable)
	{
		this(plan, inventory, worn, openedWith, storage, putBack, stackable, null);
	}

	/** @param carried the inventory slot by slot and the room in the storage, null to go by counts alone */
	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory, Map<String, Integer> worn,
		Map<String, Integer> openedWith, Map<String, Integer> storage, boolean putBack, Set<String> stackable, Carried carried)
	{
		this.plan = plan;
		this.storage = storage;
		this.carried = carried;
		this.free = carried == null ? -1 : carried.free;
		Map<String, Integer> held = storage == null ? Collections.emptyMap() : storage;
		List<ChestPlan.Line> takeOut = ChestPlan.parse(plan.getWithdraw());
		this.takeOut = takeOut;
		List<ChestPlan.Line> putIn = ChestPlan.parse(plan.getDeposit());
		List<Step> wears = new ArrayList<>();
		boolean carries = false;
		for (ChestPlan.Line line : takeOut)
		{
			if (line.wear)
			{
				int on = count(line, worn);
				wears.add(new Step(line, false, on >= line.count, 0, line.count - on));
			}
			carries |= !line.wear;
		}
		// per put-in line: how many more it takes, and whether none of its item is left
		int[] budgets = new int[putIn.size()];
		boolean[] gone = new boolean[putIn.size()];
		for (int j = 0; j < putIn.size(); j++)
		{
			ChestPlan.Line line = putIn.get(j);
			budgets[j] = Integer.MAX_VALUE;
			if (line.everythingElse)
			{
				gone[j] = true;
				for (String name : inventory.keySet())
				{
					gone[j] &= keeps(takeOut, name);
				}
			}
			else
			{
				int left = count(line, inventory);
				gone[j] = left == 0;
				if (line.counted)
				{
					int in = Math.max(count(line, openedWith) - left, count(line, held));
					budgets[j] = Math.max(0, line.count - in);
				}
			}
		}
		ChestLayout layout = carried != null && putBack && plan.isOrdered() && carries
			? new ChestLayout(takeOut, putIn, budgets, carried.names, carried.quantities, worn, storage, carried.free,
				stackable, carried.withdrawing)
			: null;
		this.layout = layout;

		List<Step> deposits = new ArrayList<>();
		List<Step> withdrawals = new ArrayList<>();
		List<String> outOfOrder = new ArrayList<>();
		List<Click> queue = new ArrayList<>();
		if (layout != null)
		{
			for (int j = 0; j < putIn.size(); j++)
			{
				boolean done = true;
				for (int slot : layout.wrong)
				{
					done &= layout.via[slot] != j;
				}
				deposits.add(new Step(putIn.get(j), true, done, 0, budgets[j]));
			}
			for (int slot : layout.wrong)
			{
				if (layout.via[slot] == ChestLayout.MISPLACED && !outOfOrder.contains(carried.names[slot]))
				{
					outOfOrder.add(carried.names[slot]);
				}
			}
			Step[] byLine = new Step[takeOut.size()];
			int order = 0;
			for (int i = 0; i < takeOut.size(); i++)
			{
				ChestPlan.Line line = takeOut.get(i);
				if (line.wear)
				{
					continue;
				}
				int any = -1;
				int first = -1;
				int more = 0;
				boolean stack = false;
				for (int k = 0; k < layout.entries.size(); k++)
				{
					ChestLayout.Entry entry = layout.entries.get(k);
					if (entry.index != i)
					{
						continue;
					}
					any = any < 0 ? k : any;
					stack = entry.stack;
					if (!layout.placed[k] || layout.shortBy[k] > 0)
					{
						first = first < 0 ? k : first;
						more += !entry.stack ? 1 : layout.placed[k] ? layout.shortBy[k] : entry.want;
					}
				}
				// no slot of its own: worn, or nowhere to get it from
				boolean missing = any < 0 && !layout.worn[i];
				int rank = first < 0 ? 0 : layout.queue.indexOf(first);
				byLine[i] = new Step(line, false, first < 0, ++order, missing, more,
					(first >= 0 ? first : any >= 0 ? any : layout.entries.size()) + 1, rank < 0 ? Integer.MAX_VALUE : rank, stack);
				withdrawals.add(byLine[i]);
			}
			for (int k : layout.queue)
			{
				queue.add(new Click(byLine[layout.entries.get(k).index], k + 1));
			}
			this.clicks = layout.entries.size();
		}
		else
		{
			for (int j = 0; j < putIn.size(); j++)
			{
				ChestPlan.Line line = putIn.get(j);
				deposits.add(new Step(line, true, gone[j] || budgets[j] == 0, 0, line.counted ? budgets[j] : Integer.MAX_VALUE));
			}
			Map<String, Integer> asked = new LinkedHashMap<>();
			// what's left in the storage for the steps further down, once the ones before took theirs
			Map<String, Integer> left = storage == null ? null : new HashMap<>(storage);
			int order = 0;
			int clicks = 0;
			int pending = 0;
			for (ChestPlan.Line line : takeOut)
			{
				if (line.wear)
				{
					continue;
				}
				// "Xeric's aid" twice means two of them
				int need = asked.merge(line.name.toLowerCase(Locale.ROOT), line.count, Integer::sum);
				int have = count(line, inventory) + count(line, worn);
				// what this line has of its own, after the same line further up took its share
				int got = Math.max(0, Math.min(line.count, have - (need - line.count)));
				boolean done = got >= line.count;
				int remaining = line.count - got;
				int there = done ? 0 : left == null ? remaining : take(line, left, remaining);
				// nowhere to get it from: skip the step rather than wait on it forever
				boolean missing = !done && left != null && there == 0;
				boolean stack = stacks(line, stackable, inventory) || stacks(line, stackable, held);
				int mine = stack ? (done || there > 0 ? 1 : 0) : got + there;
				int made = stack ? (done ? 1 : 0) : got;
				withdrawals.add(new Step(line, false, done || missing, ++order, missing, remaining, clicks + made + 1, pending, stack));
				clicks += mine;
				pending += mine - made;
			}
			this.clicks = clicks;
			if (putBack && plan.isOrdered())
			{
				int next = 0;
				while (next < withdrawals.size() && withdrawals.get(next).done)
				{
					next++;
				}
				for (String name : inventory.keySet())
				{
					// carried, not wanted by the next step or one already done, but by one further on
					boolean early = false;
					boolean later = false;
					for (int i = 0; i < withdrawals.size(); i++)
					{
						if (withdrawals.get(i).line.matches(name))
						{
							early |= i <= next;
							later |= i > next;
						}
					}
					if (later && !early)
					{
						outOfOrder.add(name);
					}
				}
			}
		}
		this.wears = Collections.unmodifiableList(wears);
		this.deposits = Collections.unmodifiableList(deposits);
		this.withdrawals = Collections.unmodifiableList(withdrawals);
		this.outOfOrder = Collections.unmodifiableList(outOfOrder);
		this.queue = Collections.unmodifiableList(queue);

		boolean round;
		boolean stuck;
		if (layout != null)
		{
			round = layout.withdrawing;
			stuck = layout.blocked;
		}
		else if (allDone(deposits) && outOfOrder.isEmpty())
		{
			round = true;
			stuck = false;
		}
		else
		{
			// a full storage: take things out to make room, and finish that round before putting more in
			boolean room = free != 0 || storage == null;
			boolean space = carried == null;
			for (int slot = 0; carried != null && slot < carried.names.length; slot++)
			{
				String name = carried.names[slot];
				space |= name == null;
				room |= name != null && storage != null && storage.containsKey(name)
					&& (pendingDeposit(name) != null || outOfOrder.contains(name));
			}
			boolean out = space && !allDone(withdrawals);
			round = carried != null && carried.withdrawing ? out || !room : !room;
			stuck = !room && !out;
			round &= out;
		}
		this.withdrawing = round;
		this.blocked = stuck;
		this.phase = !allDone(wears) ? Phase.WEAR : round ? Phase.WITHDRAW : Phase.DEPOSIT;
	}

	/** Whether the take-out list keeps an item, so "everything else" leaves it alone. */
	static boolean keeps(List<ChestPlan.Line> takeOut, String itemName)
	{
		for (ChestPlan.Line line : takeOut)
		{
			if (line.matches(itemName))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * What to do now: wear the gear, then put things in, then take things out. When the storage can't
	 * hold it all the last two alternate: withdrawals while nothing more fits, until that round is through.
	 */
	public Phase phase()
	{
		return phase;
	}

	/** Whether the plan is held to the slot: an ordered list, the put-back setting, and the inventory's slots known. */
	public boolean exact()
	{
		return layout != null;
	}

	private static boolean allDone(List<Step> steps)
	{
		for (Step step : steps)
		{
			if (!step.done)
			{
				return false;
			}
		}
		return true;
	}

	static int count(ChestPlan.Line line, Map<String, Integer> items)
	{
		int count = 0;
		for (Map.Entry<String, Integer> e : items.entrySet())
		{
			if (line.matches(e.getKey()))
			{
				count += e.getValue();
			}
		}
		return count;
	}

	/** Takes up to {@code want} of a line's items out of a container map. @return how many it got */
	static int take(ChestPlan.Line line, Map<String, Integer> items, int want)
	{
		int taken = 0;
		for (Map.Entry<String, Integer> e : items.entrySet())
		{
			if (taken < want && e.getValue() > 0 && line.matches(e.getKey()))
			{
				int some = Math.min(want - taken, e.getValue());
				e.setValue(e.getValue() - some);
				taken += some;
			}
		}
		return taken;
	}

	static boolean stacks(ChestPlan.Line line, Set<String> stackable, Map<String, Integer> items)
	{
		if (stackable.isEmpty())
		{
			return false;
		}
		for (String name : items.keySet())
		{
			if (stackable.contains(name) && line.matches(name))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Take-out lines that rebuild a loadout: the items in the order given (worn gear first, then the
	 * inventory slot by slot), a run of the same item merged into one line with its number. Dose
	 * suffixes go, so "Xeric's aid(4)" becomes "Xeric's aid". Nulls (empty slots) are skipped.
	 *
	 * @param quantities stack sizes, matching names
	 * @param prefix put before each line, "wear " for gear
	 */
	public static List<String> loadoutLines(List<String> names, List<Integer> quantities, String prefix)
	{
		List<String> lines = new ArrayList<>();
		String last = null;
		int count = 0;
		for (int i = 0; i <= names.size(); i++)
		{
			String name = i < names.size() ? names.get(i) : null;
			if (name != null)
			{
				name = DOSES.matcher(name).replaceFirst("");
			}
			if (name != null && name.equals(last))
			{
				count += quantities.get(i);
				continue;
			}
			if (last != null)
			{
				lines.add(prefix + (count > 1 ? last + ", " + count : last));
			}
			last = name;
			count = name == null ? 0 : quantities.get(i);
		}
		return lines;
	}

	private static final Pattern DOSES = Pattern.compile("\\(\\d\\)$");

	/** Item names, one per unit, to a container map; nulls (empty slots) are skipped. */
	public static Map<String, Integer> tally(List<String> names)
	{
		Map<String, Integer> items = new LinkedHashMap<>();
		for (String name : names)
		{
			if (name != null)
			{
				items.merge(name, 1, Integer::sum);
			}
		}
		return items;
	}

	/** The withdrawal to do next in an ordered plan, null when done or not ordered. */
	public Step next()
	{
		if (!plan.isOrdered())
		{
			return null;
		}
		if (!queue.isEmpty())
		{
			return queue.get(0).step;
		}
		for (Step step : withdrawals)
		{
			if (!step.done)
			{
				return step;
			}
		}
		return null;
	}

	public boolean isDone()
	{
		return allDone(wears) && (layout != null ? layout.done() : allDone(deposits) && outOfOrder.isEmpty() && allDone(withdrawals));
	}

	/** Whether an item, in the storage or the inventory, is gear still to put on. */
	public boolean highlightsWear(String itemName)
	{
		return wearStep(itemName) != null;
	}

	/** The wear step an item, in the storage or the inventory, is gear still to put on for, or null. */
	public Step wearStep(String itemName)
	{
		if (phase() != Phase.WEAR)
		{
			return null;
		}
		for (Step step : wears)
		{
			if (!step.done && step.line.matches(itemName))
			{
				return step;
			}
		}
		return null;
	}

	/** Whether an item in the side inventory should light up: something still to put in. */
	public boolean highlightsDeposit(String itemName)
	{
		if (layout != null)
		{
			if (phase == Phase.DEPOSIT)
			{
				for (int slot : layout.depositNow)
				{
					if (carried.names[slot].equals(itemName))
					{
						return true;
					}
				}
			}
			return false;
		}
		return depositStep(itemName) != null || (phase() == Phase.DEPOSIT && outOfOrder.contains(itemName));
	}

	/**
	 * Whether the item in an inventory slot goes in now. With the plan held to the slot it's that very
	 * slot, so of three aids only the one out of place lights; and only what the storage has room for.
	 * A slot that doesn't hold the item named (the caller's idea of slots isn't ours) goes by the name.
	 */
	public boolean depositsSlot(int slot, String itemName)
	{
		if (layout == null || slot < 0 || slot >= carried.names.length || !itemName.equals(carried.names[slot]))
		{
			return highlightsDeposit(itemName);
		}
		return phase == Phase.DEPOSIT && layout.depositNow.contains(slot);
	}

	/** The put-in step an inventory slot's item goes in for, null when it's just out of place (or not going in). */
	public Step depositStepAt(int slot, String itemName)
	{
		if (layout == null || slot < 0 || slot >= carried.names.length || !itemName.equals(carried.names[slot]))
		{
			return depositStep(itemName);
		}
		return phase == Phase.DEPOSIT && layout.via[slot] >= 0 ? deposits.get(layout.via[slot]) : null;
	}

	/** With the plan held to the slot: the withdrawals that can be made now, in click order. Empty in any other phase. */
	public List<Click> queue()
	{
		return phase == Phase.WITHDRAW ? queue : Collections.emptyList();
	}

	/**
	 * The deposit step an item in the side inventory still has to go in for, or null. Something
	 * carried out of order has no step; {@link #outOfOrder} lists it.
	 */
	public Step depositStep(String itemName)
	{
		if (phase() != Phase.DEPOSIT)
		{
			return null;
		}
		if (layout != null)
		{
			for (int slot : layout.depositNow)
			{
				if (carried.names[slot].equals(itemName) && layout.via[slot] >= 0)
				{
					return deposits.get(layout.via[slot]);
				}
			}
			return null;
		}
		return pendingDeposit(itemName);
	}

	/** The put-in step that still wants an item, whatever the phase. Going by counts. */
	private Step pendingDeposit(String itemName)
	{
		for (Step step : deposits)
		{
			if (step.done)
			{
				continue;
			}
			if (step.line.everythingElse)
			{
				if (!keeps(takeOut, itemName))
				{
					return step;
				}
			}
			else if (step.line.matches(itemName))
			{
				return step;
			}
		}
		return null;
	}

	/**
	 * The withdrawal step an item in the storage belongs to and hasn't been done, or null.
	 * With an ordered plan only steps within the next {@code limit} clicks count. Of the potions
	 * in the storage a step matches only the fullest lights up, so a 3-dose waits until the 4s are gone.
	 */
	public Step highlightsWithdraw(String itemName, int limit)
	{
		List<Step> steps = withdrawSteps(itemName, limit);
		return steps.isEmpty() ? null : steps.get(0);
	}

	/** Every step {@link #highlightsWithdraw} could mean, in order: "Xeric's aid" on two lines is two steps. */
	public List<Step> withdrawSteps(String itemName, int limit)
	{
		if (phase() != Phase.WITHDRAW)
		{
			return Collections.emptyList();
		}
		List<Step> steps = new ArrayList<>();
		if (layout != null)
		{
			for (int rank = 0; rank < queue.size() && rank < limit; rank++)
			{
				Step step = queue.get(rank).step;
				if (!steps.contains(step) && step.line.matches(itemName) && fullest(step.line, itemName))
				{
					steps.add(step);
				}
			}
			return steps;
		}
		for (Step step : withdrawals)
		{
			if (step.done)
			{
				continue;
			}
			if (plan.isOrdered() && step.rank >= limit)
			{
				break;
			}
			if (step.line.matches(itemName) && fullest(step.line, itemName))
			{
				steps.add(step);
			}
		}
		return steps;
	}

	/** Whether an item in the storage is what to click next: gear to wear, or the next withdrawal (any, unordered). */
	public boolean wantsNext(String itemName)
	{
		if (wearStep(itemName) != null)
		{
			return true;
		}
		return highlightsWithdraw(itemName, 1) != null;
	}

	/** Whether no fuller dose of this potion is in the storage; true for anything that isn't a potion. */
	boolean fullest(ChestPlan.Line line, String itemName)
	{
		int dose = dose(itemName);
		if (dose < 0 || storage == null)
		{
			return true;
		}
		for (String other : storage.keySet())
		{
			if (dose(other) > dose && line.matches(other))
			{
				return false;
			}
		}
		return true;
	}

	/** The N of a "(N)" dose suffix, -1 without one. */
	static int dose(String itemName)
	{
		java.util.regex.Matcher m = DOSES.matcher(itemName);
		return m.find() ? itemName.charAt(m.start() + 1) - '0' : -1;
	}
}

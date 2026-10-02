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
 * line is done once it's worn, and skipped when there's no more of it to put on. An "A | B & C" line is settled first ({@link #settle}): the first choice
 * that's all to be had, and the others aren't asked for or kept. Potions light up fullest first. Numbers are quantities, so a stack of 14
 * juice counts as 14. Containers are maps of item name to quantity; a null storage is one the client
 * hasn't seen, so nothing is skipped for not being in it.
 *
 * <p>With putBack, an ordered plan is held to the slot: given the inventory slot by slot ({@link Carried})
 * it goes by {@link ChestLayout}, where a withdrawal is done when its slot holds it and anything in
 * the wrong slot goes back in. Without the slots it only asks back what belongs to a later step.
 *
 * <p>Held to the slot, something out of place goes in before the next withdrawal, as soon as it is.
 * A storage that's full turns the phases into rounds: put in what fits, take out, put in the rest.
 * Every item takes a storage slot of its own; only a stackable one joins the stack already in there.
 * Held to the slot, a plan in a storage that tight also asks for drags inside the inventory ({@link Phase#MOVE}).
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
		/** A withdrawal or gear to wear skipped because no more of the item is on you or in the storage; done as well. */
		public final boolean missing;
		/** How many more still to move, so only that many light up; MAX_VALUE for all of them. */
		public final int remaining;
		/**
		 * Withdrawals: the number of this step's next click, counting every click of the plan from 1.
		 * "Xeric's aid, 2" is two clicks, a stack is one, and a skipped step is none.
		 */
		public final int click;
		/**
		 * Withdrawals: clicks still to make before this step's next one, 0 for the very next click;
		 * MAX_VALUE for one that can't be made yet. The clicks are numbered by it, from 1.
		 */
		public final int rank;
		/** Withdrawals: how many clicks of the step can be made now, one after the other. */
		public final int now;
		/** Withdrawals: whether it comes out as one stack, so one click whatever the count. */
		public final boolean stack;
		/** Withdrawals: the number of the step's first click, done ones included. */
		public final int first;
		/** Withdrawals: how many clicks the step is; 0 for one that's worn or nowhere to be found, which has no number. */
		public final int span;

		Step(ChestPlan.Line line, boolean deposit, boolean done, int order, int remaining)
		{
			this(line, deposit, done, order, false, remaining, 0, 0, false, 0, 0, 0);
		}

		Step(ChestPlan.Line line, boolean deposit, boolean done, int order, boolean missing, int remaining,
			int click, int rank, boolean stack, int first, int span, int now)
		{
			this.now = now;
			this.first = first;
			this.span = span;
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
		WEAR, DEPOSIT, WITHDRAW,
		/** Drag an item to the slot it belongs in. */
		MOVE
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
		/** Whether that round was of withdrawals into whatever slots were empty ({@link ChestProgress#loose}). */
		final boolean loose;
		/** The clicks the numbers leave out ({@link ChestProgress#numbered}), null to number from the next click. */
		final boolean[] numbered;

		public Carried(String[] names, int[] quantities, int free, boolean withdrawing)
		{
			this(names, quantities, free, withdrawing, false);
		}

		public Carried(String[] names, int[] quantities, int free, boolean withdrawing, boolean loose)
		{
			this(names, quantities, free, withdrawing, loose, null);
		}

		public Carried(String[] names, int[] quantities, int free, boolean withdrawing, boolean loose, boolean[] numbered)
		{
			this.names = names;
			this.quantities = quantities;
			this.free = free;
			this.withdrawing = withdrawing;
			this.loose = loose;
			this.numbered = numbered;
		}

		public Carried(List<String> names)
		{
			this(names.toArray(new String[0]), ones(names.size()), -1, false);
		}

		public Carried with(int free, boolean withdrawing, boolean loose)
		{
			return new Carried(names, quantities, free, withdrawing, loose, numbered);
		}

		public Carried with(int free, boolean withdrawing, boolean loose, boolean[] numbered)
		{
			return new Carried(names, quantities, free, withdrawing, loose, numbered);
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
	/**
	 * Carried items to put back before taking out: in the wrong slot, or without the slots, of a later step.
	 * One that gets dragged to its slot instead ({@link #drags}) isn't among them.
	 */
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
	/** With a layout, the same plan going by counts alone: what's carried, whatever slot it's in. */
	private final ChestProgress counts;
	private final Set<String> stackable;
	/** Per click of the plan, from 1, whether it was made already when the numbering began. */
	private final boolean[] numbered;
	/** How many withdrawals can be made now, one after the other. */
	private final int toClick;

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
		this.stackable = stackable;
		this.free = carried == null ? -1 : carried.free;
		Map<String, Integer> held = storage == null ? Collections.emptyMap() : storage;
		List<ChestPlan.Line> takeOut = settle(ChestPlan.parse(plan.getWithdraw()), inventory, worn, storage);
		this.takeOut = takeOut;
		List<ChestPlan.Line> putIn = ChestPlan.parse(plan.getDeposit());
		List<Step> wears = new ArrayList<>();
		boolean carries = false;
		for (ChestPlan.Line line : takeOut)
		{
			if (line.wear)
			{
				int on = count(line, worn);
				// no more of it to put on, carried or in the storage: skipped like a withdrawal that's nowhere,
				// so arrows shot down below their number don't hold up everything after them
				boolean missing = on < line.count && storage != null && count(line, inventory) == 0 && count(line, storage) == 0;
				wears.add(new Step(line, false, on >= line.count || missing, 0, missing, line.count - on, 0, 0, false, 0, 0, 0));
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
				stackable, carried.withdrawing, carried.loose)
			: null;
		this.layout = layout;
		this.counts = layout == null ? null : new ChestProgress(plan, inventory, worn, openedWith, storage, false, stackable, carried);

		List<Step> deposits = new ArrayList<>();
		List<Step> withdrawals = new ArrayList<>();
		List<String> outOfOrder = new ArrayList<>();
		List<Click> queue = new ArrayList<>();
		// the clicks of the plan, from 1, that are made already
		java.util.BitSet madeClicks = new java.util.BitSet();
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
			Set<Integer> dragged = new java.util.HashSet<>();
			for (int[] move : layout.moves)
			{
				dragged.add(move[0]);
			}
			for (int slot : layout.wrong)
			{
				if (layout.via[slot] == ChestLayout.MISPLACED && !dragged.contains(slot) && !outOfOrder.contains(carried.names[slot]))
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
				int total = 0;
				int first = -1;
				int more = 0;
				int rank = -1;
				int now = 0;
				boolean stack = false;
				for (int k = 0; k < layout.entries.size(); k++)
				{
					ChestLayout.Entry entry = layout.entries.get(k);
					if (entry.index != i)
					{
						continue;
					}
					any = any < 0 ? k : any;
					total++;
					stack = entry.stack;
					int queued = layout.queue.indexOf(k);
					if (queued >= 0)
					{
						rank = rank < 0 ? queued : rank;
						now++;
					}
					if (!layout.placed[k] || layout.shortBy[k] > 0)
					{
						first = first < 0 ? k : first;
						more += !entry.stack ? 1 : layout.placed[k] ? layout.shortBy[k] : entry.want;
					}
				}
				// no slot of its own: worn, or nowhere to get it from
				boolean missing = any < 0 && !layout.worn[i];
				byLine[i] = new Step(line, false, first < 0, ++order, missing, more,
					(first >= 0 ? first : any >= 0 ? any : layout.entries.size()) + 1, first < 0 ? 0 : rank < 0 ? Integer.MAX_VALUE : rank, stack,
					any + 1, total, now);
				withdrawals.add(byLine[i]);
			}
			for (int k : layout.queue)
			{
				queue.add(new Click(byLine[layout.entries.get(k).index], k + 1));
			}
			this.clicks = layout.entries.size();
			this.toClick = layout.queue.size();
			for (int k = 0; k < layout.entries.size(); k++)
			{
				madeClicks.set(k + 1, layout.placed[k] && layout.shortBy[k] == 0);
			}
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
				withdrawals.add(new Step(line, false, done || missing, ++order, missing, remaining, clicks + made + 1, pending, stack,
					clicks + 1, mine, mine - made));
				madeClicks.set(clicks + 1, clicks + 1 + made);
				clicks += mine;
				pending += mine - made;
			}
			this.clicks = clicks;
			this.toClick = pending;
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
		boolean[] done = new boolean[this.clicks + 1];
		for (int click = 1; click < done.length; click++)
		{
			done[click] = madeClicks.get(click);
		}
		// the numbers stay as they were handed in while that still fits: the same list, and nothing it left out undone since
		boolean[] from = carried == null ? null : carried.numbered;
		boolean fits = from != null && from.length == done.length;
		for (int click = 1; fits && click < done.length; click++)
		{
			fits = !from[click] || done[click];
		}
		this.numbered = fits ? from : done;

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
				room |= name != null && freeDeposit(name) && (pendingDeposit(name) != null || outOfOrder.contains(name));
			}
			boolean out = space && !allDone(withdrawals);
			round = carried != null && carried.withdrawing ? out || !room : !room;
			stuck = !room && !out;
			round &= out;
		}
		this.withdrawing = round;
		this.blocked = stuck;
		this.phase = !allDone(wears) ? Phase.WEAR : layout != null && layout.moving ? Phase.MOVE : round ? Phase.WITHDRAW : Phase.DEPOSIT;
	}

	/**
	 * The take-out list with each "A | B & C" line turned into the items of one of its choices: the first
	 * whose items are all to be had, on you or in the storage, in the numbers asked for. So with the bow
	 * around, the chinchompas and the buckler of "Venator bow | *chinchompa & Twisted buckler" aren't asked
	 * for, don't light up and aren't kept. With no choice complete, a line of single items under one count
	 * ("Ayak | Sang* staff*") stays as it is and takes any of them; any other goes by the first choice
	 * there's something of, or the very first. A storage the client hasn't seen counts as empty here.
	 */
	static List<ChestPlan.Line> settle(List<ChestPlan.Line> lines, Map<String, Integer> inventory, Map<String, Integer> worn,
		Map<String, Integer> storage)
	{
		List<ChestPlan.Line> settled = new ArrayList<>();
		for (ChestPlan.Line line : lines)
		{
			if (line.options.isEmpty() || line.everything || line.everythingElse)
			{
				settled.add(line);
				continue;
			}
			List<ChestPlan.Line> complete = null;
			List<ChestPlan.Line> begun = null;
			for (List<ChestPlan.Line> option : line.options)
			{
				boolean all = true;
				boolean any = false;
				for (ChestPlan.Line item : option)
				{
					int have = count(item, inventory) + count(item, worn) + (storage == null ? 0 : count(item, storage));
					all &= have >= item.count;
					any |= have > 0;
				}
				if (all)
				{
					complete = option;
					break;
				}
				begun = begun == null && any ? option : begun;
			}
			if (complete == null && line.either)
			{
				settled.add(line);
			}
			else
			{
				settled.addAll(complete != null ? complete : begun != null ? begun : line.options.get(0));
			}
		}
		return settled;
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
	 * hold it all the last two alternate: withdrawals while nothing more fits, until that round is through,
	 * with drags in between for what's carried in the wrong slot.
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

	/**
	 * Whether the chest has been seen to: the gear is on, what goes in is in and what comes out is
	 * carried, in whatever slots. A plan held to the slot can have this and still not be {@link #isDone}.
	 */
	public boolean isStocked()
	{
		return counts == null ? isDone() : counts.isDone();
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

	/** How many of an item go in now, with the plan held to the slot; for a caller that can't tell the slots apart. */
	public int depositsNamed(String itemName)
	{
		int count = 0;
		if (layout != null && phase == Phase.DEPOSIT)
		{
			for (int slot : layout.depositNow)
			{
				count += carried.names[slot].equals(itemName) ? 1 : 0;
			}
		}
		return count;
	}

	/** Whether putting an item in takes no storage slot: the room isn't known, or it joins a stack that's in there. */
	public boolean freeDeposit(String itemName)
	{
		return free < 0 || storage == null || (stackable.contains(itemName) && storage.containsKey(itemName));
	}

	/** The drags to make now, as {from, to} inventory slots, the next one first. Empty in any other phase. */
	public List<int[]> moves()
	{
		return phase == Phase.MOVE ? layout.moves : Collections.emptyList();
	}

	/**
	 * Whether the withdrawals to make now land out of place, in whatever slots are empty, and get dragged
	 * to their own after. To hand back in with the next {@link Carried}, like {@link #withdrawing}.
	 */
	public boolean loose()
	{
		return phase == Phase.WITHDRAW && layout != null && layout.loose;
	}

	/** Every drag the plan asks for, whether it's their turn yet or not. */
	public List<int[]> drags()
	{
		return layout == null ? Collections.emptyList() : layout.moves;
	}

	/** Whether one of {@link #outOfOrder} goes back in right now: it's the deposits' turn and the storage has room for it. */
	public boolean redepositsNow(String itemName)
	{
		if (phase != Phase.DEPOSIT)
		{
			return false;
		}
		if (layout == null)
		{
			return outOfOrder.contains(itemName);
		}
		for (int slot : layout.depositNow)
		{
			if (layout.via[slot] == ChestLayout.MISPLACED && carried.names[slot].equals(itemName))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * With the storage too full to put it back in: the inventory slot of an item to drag to the slot it
	 * belongs in, -1 when there's no such thing to do. What's in that slot swaps places with it.
	 */
	public int moveFrom()
	{
		return phase == Phase.MOVE ? layout.moves.get(0)[0] : -1;
	}

	/** The inventory slot {@link #moveFrom} goes to. */
	public int moveTo()
	{
		return phase == Phase.MOVE ? layout.moves.get(0)[1] : -1;
	}

	/** The name of the item in an inventory slot, null for an empty one or without the slots. */
	public String nameAt(int slot)
	{
		return carried == null || slot < 0 || slot >= carried.names.length ? null : carried.names[slot];
	}

	/** The name of the item to drag, null for none. */
	public String moveName()
	{
		return nameAt(moveFrom());
	}

	/**
	 * The number a click of the plan is shown with. The clicks still to make when the storage was opened
	 * count from 1, and each keeps its number while the ones before it get made, so what's lit reads
	 * 1 2 3 4, then 2 3 4 5.
	 *
	 * @param click its place in the whole plan, from 1
	 */
	public int number(int click)
	{
		int number = click;
		for (int before = 1; before < click && before < numbered.length; before++)
		{
			number -= numbered[before] ? 1 : 0;
		}
		return number;
	}

	/** The number of a withdrawal step's next click. */
	public int number(Step step)
	{
		for (Click click : queue)
		{
			if (click.step == step)
			{
				return number(click.number);
			}
		}
		return number(step.click);
	}

	/** What the numbers leave out, to hand back in with the next {@link Carried} so they stay as they are. */
	public boolean[] numbered()
	{
		return numbered;
	}

	/** How many withdrawals can be made now, one after the other. */
	public int toClick()
	{
		return phase == Phase.WITHDRAW ? toClick : 0;
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

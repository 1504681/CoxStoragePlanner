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
 * line is done once it's worn. With putBack, an ordered plan also wants anything carried that belongs
 * to a later step put back first, so it can come out in its place. Potions light up fullest first.
 * Numbers are quantities, so a stack of 14 juice counts as 14. Containers are maps of item name to quantity;
 * a null storage is one the client hasn't seen, so nothing is skipped for not being in it.
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

	public final ChestPlan plan;
	/** Gear to put on, before anything else. */
	public final List<Step> wears;
	public final List<Step> deposits;
	public final List<Step> withdrawals;
	/** Carried items that belong to a later step of an ordered plan, to put back before taking out. */
	public final List<String> outOfOrder;
	/** Clicks the whole take-out list comes to, skipped steps left out. */
	public final int clicks;
	/** What the storage holds, null when the client hasn't seen it. */
	private final Map<String, Integer> storage;

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
		this.plan = plan;
		this.storage = storage;
		Map<String, Integer> held = storage == null ? Collections.emptyMap() : storage;
		List<ChestPlan.Line> takeOut = ChestPlan.parse(plan.getWithdraw());
		List<Step> wears = new ArrayList<>();
		for (ChestPlan.Line line : takeOut)
		{
			if (line.wear)
			{
				int on = count(line, worn);
				wears.add(new Step(line, false, on >= line.count, 0, line.count - on));
			}
		}
		List<Step> deposits = new ArrayList<>();
		for (ChestPlan.Line line : ChestPlan.parse(plan.getDeposit()))
		{
			boolean done;
			int remaining = Integer.MAX_VALUE;
			if (line.everythingElse)
			{
				done = true;
				for (String name : inventory.keySet())
				{
					done &= keeps(takeOut, name);
				}
			}
			else
			{
				int left = count(line, inventory);
				int in = Math.max(count(line, openedWith) - left, count(line, held));
				done = left == 0 || (line.counted && in >= line.count);
				if (line.counted)
				{
					remaining = line.count - in;
				}
			}
			deposits.add(new Step(line, true, done, 0, remaining));
		}
		List<Step> withdrawals = new ArrayList<>();
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
		List<String> outOfOrder = new ArrayList<>();
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
		this.wears = Collections.unmodifiableList(wears);
		this.deposits = Collections.unmodifiableList(deposits);
		this.withdrawals = Collections.unmodifiableList(withdrawals);
		this.outOfOrder = Collections.unmodifiableList(outOfOrder);
	}

	/** Whether the take-out list keeps an item, so "everything else" leaves it alone. */
	private static boolean keeps(List<ChestPlan.Line> takeOut, String itemName)
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

	/** What to do now: wear the gear, then put things in, then take things out. */
	public Phase phase()
	{
		if (!allDone(wears))
		{
			return Phase.WEAR;
		}
		return allDone(deposits) && outOfOrder.isEmpty() ? Phase.WITHDRAW : Phase.DEPOSIT;
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

	private static int count(ChestPlan.Line line, Map<String, Integer> items)
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
	private static int take(ChestPlan.Line line, Map<String, Integer> items, int want)
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

	private static boolean stacks(ChestPlan.Line line, Set<String> stackable, Map<String, Integer> items)
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
		return allDone(wears) && allDone(deposits) && outOfOrder.isEmpty() && allDone(withdrawals);
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
		return depositStep(itemName) != null || (phase() == Phase.DEPOSIT && outOfOrder.contains(itemName));
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
		List<ChestPlan.Line> takeOut = null;
		for (Step step : deposits)
		{
			if (step.done)
			{
				continue;
			}
			if (step.line.everythingElse)
			{
				if (takeOut == null)
				{
					takeOut = ChestPlan.parse(plan.getWithdraw());
				}
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
	private boolean fullest(ChestPlan.Line line, String itemName)
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

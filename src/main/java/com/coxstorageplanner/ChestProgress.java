package com.coxstorageplanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A chest's plan checked against the inventory right now, in three phases: gear to wear first, then
 * deposits, then withdrawals. A deposit is done when none of the item is left, or, for a line with a
 * number, when that many went in since the storage was opened or are already in it; "everything else"
 * is done when nothing is carried that the take-out list doesn't keep. A withdrawal is done when the
 * inventory and worn equipment together hold the number asked for; the same item on a later line asks
 * for that many more. A "wear" line is done once it's worn. Numbers are quantities, so a stack of 14
 * juice counts as 14. Containers are maps of item name to quantity.
 */
public final class ChestProgress
{
	public static final class Step
	{
		public final ChestPlan.Line line;
		public final boolean deposit;
		public final boolean done;
		/** Position in the withdraw order, 1-based, 0 for deposits. */
		public final int order;

		Step(ChestPlan.Line line, boolean deposit, boolean done, int order)
		{
			this.line = line;
			this.deposit = deposit;
			this.done = done;
			this.order = order;
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

	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory)
	{
		this(plan, inventory, Collections.emptyMap(), inventory, Collections.emptyMap());
	}

	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory, Map<String, Integer> openedWith, Map<String, Integer> storage)
	{
		this(plan, inventory, Collections.emptyMap(), openedWith, storage);
	}

	/**
	 * @param worn what's equipped, which counts as withdrawn too so gear you put on stays ticked off
	 * @param openedWith the inventory when the storage was opened
	 * @param storage what the storage holds now
	 */
	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory, Map<String, Integer> worn,
		Map<String, Integer> openedWith, Map<String, Integer> storage)
	{
		this.plan = plan;
		List<ChestPlan.Line> takeOut = ChestPlan.parse(plan.getWithdraw());
		List<Step> wears = new ArrayList<>();
		for (ChestPlan.Line line : takeOut)
		{
			if (line.wear)
			{
				wears.add(new Step(line, false, count(line, worn) >= line.count, 0));
			}
		}
		List<Step> deposits = new ArrayList<>();
		for (ChestPlan.Line line : ChestPlan.parse(plan.getDeposit()))
		{
			boolean done;
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
				done = left == 0 || (line.counted
					&& (count(line, openedWith) - left >= line.count || count(line, storage) >= line.count));
			}
			deposits.add(new Step(line, true, done, 0));
		}
		List<Step> withdrawals = new ArrayList<>();
		Map<String, Integer> asked = new LinkedHashMap<>();
		int order = 0;
		for (ChestPlan.Line line : takeOut)
		{
			if (line.wear)
			{
				continue;
			}
			// "Xeric's aid" twice means two of them
			int need = asked.merge(line.name.toLowerCase(Locale.ROOT), line.count, Integer::sum);
			boolean done = count(line, inventory) + count(line, worn) >= need;
			withdrawals.add(new Step(line, false, done, ++order));
		}
		this.wears = Collections.unmodifiableList(wears);
		this.deposits = Collections.unmodifiableList(deposits);
		this.withdrawals = Collections.unmodifiableList(withdrawals);
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
		return allDone(deposits) ? Phase.WITHDRAW : Phase.DEPOSIT;
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
		return allDone(wears) && allDone(deposits) && allDone(withdrawals);
	}

	/** Whether an item, in the storage or the inventory, is gear still to put on. */
	public boolean highlightsWear(String itemName)
	{
		if (phase() != Phase.WEAR)
		{
			return false;
		}
		for (Step step : wears)
		{
			if (!step.done && step.line.matches(itemName))
			{
				return true;
			}
		}
		return false;
	}

	/** Whether an item in the side inventory should light up: something still to put in. */
	public boolean highlightsDeposit(String itemName)
	{
		if (phase() != Phase.DEPOSIT)
		{
			return false;
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
					return true;
				}
			}
			else if (step.line.matches(itemName))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The withdrawal step an item in the storage belongs to and hasn't been done, or null.
	 * With an ordered plan only the next {@code limit} steps still to do count.
	 */
	public Step highlightsWithdraw(String itemName, int limit)
	{
		if (phase() != Phase.WITHDRAW)
		{
			return null;
		}
		int rank = 0;
		for (Step step : withdrawals)
		{
			if (step.done)
			{
				continue;
			}
			if (plan.isOrdered() && rank >= limit)
			{
				return null;
			}
			if (step.line.matches(itemName))
			{
				return step;
			}
			rank++;
		}
		return null;
	}

	/** How many withdrawals still to do come before this one: 0 for the next one. */
	public int rank(Step step)
	{
		int rank = 0;
		for (Step other : withdrawals)
		{
			if (other == step)
			{
				return rank;
			}
			if (!other.done)
			{
				rank++;
			}
		}
		return -1;
	}
}

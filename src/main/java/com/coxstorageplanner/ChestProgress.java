package com.coxstorageplanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A chest's plan checked against the inventory right now. A deposit is done when none of the item is
 * left, or, for a line with a number, when that many went in since the storage was opened or are
 * already in it. A withdrawal is done when the inventory holds the number asked for. Numbers are
 * quantities, so a stack of 14 juice counts as 14. Containers are maps of item name to quantity.
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

	public final ChestPlan plan;
	public final List<Step> deposits;
	public final List<Step> withdrawals;

	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory)
	{
		this(plan, inventory, inventory, Collections.emptyMap());
	}

	/**
	 * @param openedWith the inventory when the storage was opened
	 * @param storage what the storage holds now
	 */
	public ChestProgress(ChestPlan plan, Map<String, Integer> inventory, Map<String, Integer> openedWith, Map<String, Integer> storage)
	{
		this.plan = plan;
		List<Step> deposits = new ArrayList<>();
		for (ChestPlan.Line line : ChestPlan.parse(plan.getDeposit()))
		{
			int left = count(line, inventory);
			boolean done = left == 0 || (line.counted
				&& (count(line, openedWith) - left >= line.count || count(line, storage) >= line.count));
			deposits.add(new Step(line, true, done, 0));
		}
		List<Step> withdrawals = new ArrayList<>();
		int order = 0;
		for (ChestPlan.Line line : ChestPlan.parse(plan.getWithdraw()))
		{
			withdrawals.add(new Step(line, false, count(line, inventory) >= line.count, ++order));
		}
		this.deposits = Collections.unmodifiableList(deposits);
		this.withdrawals = Collections.unmodifiableList(withdrawals);
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
		for (Step step : deposits)
		{
			if (!step.done)
			{
				return false;
			}
		}
		for (Step step : withdrawals)
		{
			if (!step.done)
			{
				return false;
			}
		}
		return true;
	}

	/** Whether an item in the side inventory should light up: something still to put in. */
	public boolean highlightsDeposit(String itemName)
	{
		for (Step step : deposits)
		{
			if (!step.done && step.line.matches(itemName))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The withdrawal step an item in the storage belongs to and hasn't been done, or null.
	 * With an ordered plan and nextOnly, only the next step counts.
	 */
	public Step highlightsWithdraw(String itemName, boolean nextOnly)
	{
		if (plan.isOrdered() && nextOnly)
		{
			Step next = next();
			return next != null && next.line.matches(itemName) ? next : null;
		}
		for (Step step : withdrawals)
		{
			if (!step.done && step.line.matches(itemName))
			{
				return step;
			}
		}
		return null;
	}
}

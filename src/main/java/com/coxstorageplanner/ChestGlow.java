package com.coxstorageplanner;

/** Which items in the storage light up when a chest's withdrawals are ordered. */
public enum ChestGlow
{
	NEXT_ONLY("Only the next click", 1),
	/** How many is a setting of its own; four unless that says otherwise. */
	NEXT_FOUR("Next few clicks, biggest first", 4),
	GRADIENT("All, first to last", Integer.MAX_VALUE);

	private final String displayName;
	/** How many of the clicks still to make light up, from the next one on. */
	private final int steps;

	ChestGlow(String displayName, int steps)
	{
		this.displayName = displayName;
		this.steps = steps;
	}

	public int getSteps()
	{
		return steps;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}

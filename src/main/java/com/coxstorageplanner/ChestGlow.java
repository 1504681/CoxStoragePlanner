package com.coxstorageplanner;

/** Which items in the storage light up when a chest's withdrawals are ordered. */
public enum ChestGlow
{
	NEXT_ONLY("Only the next one"),
	GRADIENT("All, first to last");

	private final String displayName;

	ChestGlow(String displayName)
	{
		this.displayName = displayName;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}

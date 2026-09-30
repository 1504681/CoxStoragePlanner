package com.coxstorageplanner;

/** How dose counts are shown and typed in the sidebar. Everything is stored as doses. */
public enum NeedUnits
{
	DOSES("Doses", "doses"),
	POTIONS("Potions", "potions");

	private final String displayName;
	private final String word;

	NeedUnits(String displayName, String word)
	{
		this.displayName = displayName;
		this.word = word;
	}

	/** The unit word after a number. */
	public String getWord()
	{
		return word;
	}

	/** A dose count in these units: 9 doses is "9" or "2.25". */
	public String format(int doses)
	{
		if (this == DOSES || doses % Potion.DOSES_PER_POTION == 0)
		{
			return String.valueOf(doses / (this == DOSES ? 1 : Potion.DOSES_PER_POTION));
		}
		String text = String.valueOf(doses / (double) Potion.DOSES_PER_POTION);
		return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
	}

	/** Doses for something typed in these units, 0 for anything that isn't a number. */
	public int parse(String text)
	{
		try
		{
			double value = Double.parseDouble(text.trim());
			return (int) Math.round(this == DOSES ? value : value * Potion.DOSES_PER_POTION);
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}

package com.coxstorageplanner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Doses you want to have when you get to Olm, per potion. Stored as "POTION:doses,...".
 * Nothing set at all is stored as "none" so it isn't mistaken for "never set".
 */
public final class Needs
{
	public static final int MAX_DOSES = 99;
	private static final String NONE = "none";

	private final Map<Potion, Integer> doses = new EnumMap<>(Potion.class);

	/** A first plan for a team. */
	public static Needs defaults()
	{
		Needs needs = new Needs();
		needs.set(Potion.OVERLOAD, 4);
		needs.set(Potion.XERICS_AID, 24);
		needs.set(Potion.REVITALISATION, 12);
		needs.set(Potion.PRAYER_ENHANCE, 4);
		return needs;
	}

	/** A first plan for a solo: more running, so another revitalisation. */
	public static Needs soloDefaults()
	{
		Needs needs = defaults();
		needs.set(Potion.REVITALISATION, 16);
		return needs;
	}

	/** The given defaults for something never stored, otherwise whatever parses. */
	public static Needs parse(String encoded, Needs defaults)
	{
		if (encoded == null || encoded.isEmpty())
		{
			return defaults;
		}
		Needs needs = new Needs();
		for (String entry : encoded.split(","))
		{
			String[] parts = entry.split(":");
			try
			{
				if (parts.length == 2)
				{
					needs.set(Potion.valueOf(parts[0]), Integer.parseInt(parts[1]));
				}
				else if (parts.length == 3)
				{
					// the per-room form of 1.0.0: add the rooms up
					Potion potion = Potion.valueOf(parts[1]);
					needs.set(potion, needs.get(potion) + Integer.parseInt(parts[2]));
				}
			}
			catch (IllegalArgumentException e)
			{
				// an entry from another version
			}
		}
		return needs;
	}

	public synchronized String encode()
	{
		List<String> entries = new ArrayList<>();
		for (Map.Entry<Potion, Integer> e : doses.entrySet())
		{
			if (e.getValue() > 0)
			{
				entries.add(e.getKey().name() + ":" + e.getValue());
			}
		}
		return entries.isEmpty() ? NONE : String.join(",", entries);
	}

	public synchronized Needs copy()
	{
		return parse(encode(), new Needs());
	}

	public synchronized int get(Potion potion)
	{
		return doses.getOrDefault(potion, 0);
	}

	/** @return whether anything changed */
	public synchronized boolean set(Potion potion, int count)
	{
		count = Math.max(0, Math.min(MAX_DOSES, count));
		if (get(potion) == count)
		{
			return false;
		}
		doses.put(potion, count);
		return true;
	}
}

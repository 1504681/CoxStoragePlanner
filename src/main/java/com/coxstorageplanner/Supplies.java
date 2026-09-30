package com.coxstorageplanner;

import java.util.Arrays;

/** Doses per potion for one place (inventory, private storage, shared storage). */
public final class Supplies
{
	public static final Supplies EMPTY = new Supplies(new int[Potion.values().length]);

	private final int[] doses;

	private Supplies(int[] doses)
	{
		this.doses = doses;
	}

	/** @param doses doses per potion, in {@link Potion} order; missing entries count as 0 */
	public static Supplies of(int[] doses)
	{
		int[] copy = new int[Potion.values().length];
		if (doses != null)
		{
			for (int i = 0; i < copy.length && i < doses.length; i++)
			{
				copy[i] = Math.max(0, doses[i]);
			}
		}
		return new Supplies(copy);
	}

	/** Counts the doses in a container given as parallel item id / quantity arrays. */
	public static Supplies count(int[] itemIds, int[] quantities)
	{
		int[] doses = new int[Potion.values().length];
		for (int i = 0; i < itemIds.length; i++)
		{
			Potion potion = Potion.of(itemIds[i]);
			if (potion != null)
			{
				doses[potion.ordinal()] += potion.doses(itemIds[i]) * Math.max(1, quantities[i]);
			}
		}
		return new Supplies(doses);
	}

	public int doses(Potion potion)
	{
		return doses[potion.ordinal()];
	}

	/** Doses of elder, twisted and kodai that can be drunk as a set, which is what an overload dose is. */
	public int splitOverloadDoses()
	{
		return Math.min(doses(Potion.ELDER), Math.min(doses(Potion.TWISTED), doses(Potion.KODAI)));
	}

	public Supplies plus(Supplies other)
	{
		int[] sum = new int[doses.length];
		for (int i = 0; i < sum.length; i++)
		{
			sum[i] = doses[i] + other.doses[i];
		}
		return new Supplies(sum);
	}

	public int[] toArray()
	{
		return doses.clone();
	}

	@Override
	public boolean equals(Object o)
	{
		return o instanceof Supplies && Arrays.equals(doses, ((Supplies) o).doses);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(doses);
	}

	@Override
	public String toString()
	{
		return Arrays.toString(doses);
	}
}

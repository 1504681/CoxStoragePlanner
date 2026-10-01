package com.coxstorageplanner;

/**
 * The raid potions. Every raid potion has 12 consecutive item ids:
 * (-) 1-4 dose, regular 1-4 dose, (+) 1-4 dose.
 * The supply potions get a row in the sidebar totals. Elder, twisted and kodai are only counted
 * as the three parts of a split overload, which is what Vanguards drop one set of.
 */
public enum Potion
{
	OVERLOAD("Overload", "Ovl", Ids.raid(20985), true, true),
	XERICS_AID("Xeric's aid", "Aid", Ids.raid(20973), true, true),
	REVITALISATION("Revitalisation", "Revit", Ids.raid(20949), true, true),
	PRAYER_ENHANCE("Prayer enhance", "Enh", Ids.raid(20961), true, true),
	ELDER("Elder", "Elder", Ids.raid(20913), false, false),
	TWISTED("Twisted", "Twisted", Ids.raid(20925), false, false),
	KODAI("Kodai", "Kodai", Ids.raid(20937), false, false),
	/** One elder, one twisted and one kodai, claimed together. Not an item of its own. */
	SPLIT_OVERLOAD("Split overload", "Split", new int[0], false, true);

	public static final int DOSES_PER_POTION = 4;

	private static final class Ids
	{
		/** The 12 ids of a raid potion, starting at the (-) 1 dose. */
		private static int[] raid(int first)
		{
			int[] ids = new int[3 * DOSES_PER_POTION];
			for (int i = 0; i < ids.length; i++)
			{
				ids[i] = first + i;
			}
			return ids;
		}
	}

	private final String displayName;
	private final String shortName;
	/** Item ids, the one at index i holding i % 4 + 1 doses. */
	private final int[] itemIds;
	private final boolean supply;
	private final boolean claimable;

	Potion(String displayName, String shortName, int[] itemIds, boolean supply, boolean claimable)
	{
		this.displayName = displayName;
		this.shortName = shortName;
		this.itemIds = itemIds;
		this.supply = supply;
		this.claimable = claimable;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getShortName()
	{
		return shortName;
	}

	/** Whether the sidebar totals and the "needed" settings cover this potion. */
	public boolean isSupply()
	{
		return supply;
	}

	/** Whether rooms drop it as something to claim. */
	public boolean isClaimable()
	{
		return claimable;
	}

	/** Item id of the full potion, used for the sidebar icon. */
	public int getIconItemId()
	{
		return itemIds.length == 0 ? -1 : itemIds[itemIds.length - 1];
	}

	public boolean matches(int itemId)
	{
		return doses(itemId) > 0;
	}

	/** Doses in one item with this id, 0 if it isn't this potion. */
	public int doses(int itemId)
	{
		for (int i = 0; i < itemIds.length; i++)
		{
			if (itemIds[i] == itemId)
			{
				return i % DOSES_PER_POTION + 1;
			}
		}
		return 0;
	}

	public static Potion of(int itemId)
	{
		for (Potion potion : values())
		{
			if (potion.matches(itemId))
			{
				return potion;
			}
		}
		return null;
	}
}

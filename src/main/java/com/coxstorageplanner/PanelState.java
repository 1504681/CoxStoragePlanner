package com.coxstorageplanner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A snapshot of everything the sidebar shows, built by the plugin and drawn on the Swing thread. */
final class PanelState
{
	static final class Member
	{
		String name;
		boolean self;
		/** null until the member's plugin has sent something */
		MemberSupplies status;
	}

	boolean inParty;
	boolean countShared;
	boolean countSplit;

	Supplies inventory = Supplies.EMPTY;
	/** null until the storage has been opened this raid */
	Supplies privateStorage;
	Supplies sharedStorage;
	final Map<Potion, Integer> need = new EnumMap<>(Potion.class);
	/** Whether the numbers are for a solo raid: the raid's size while in one, the tab otherwise. */
	boolean solo;
	boolean separateSoloNeeds;
	/** Whether solo raids have their own chest plans, so {@link #chests} is the set for {@link #solo}. */
	boolean separateSoloChests;
	boolean trackStamina;
	NeedUnits units = NeedUnits.POTIONS;
	ChestBook chests = new ChestBook();
	/** Chest of the room the player is in, null outside one. */
	String currentChest;
	/** Item name to quantity in the inventory, for the steps of a chest that isn't open. */
	Map<String, Integer> carriedItems = new LinkedHashMap<>();
	/** Item name to quantity worn, which counts as withdrawn. */
	Map<String, Integer> wornItems = new LinkedHashMap<>();
	/** Progress at the storage that's open right now, null when none is. */
	ChestProgress openChest;
	/** Whether clicking items in a storage or the inventory adds them to the chest's lists. */
	boolean marking;
	/** Whether an ordered plan wants carried items of later steps put back first. */
	boolean putBack;

	final List<Member> team = new ArrayList<>();

	/** Everything within reach: inventory, private storage, and shared storage if that counts. */
	private Supplies held()
	{
		Supplies held = inventory;
		if (privateStorage != null)
		{
			held = held.plus(privateStorage);
		}
		Supplies shared = shared();
		if (countShared && shared != null)
		{
			held = held.plus(shared);
		}
		return held;
	}

	/** The shared storage as you saw it, or as a party member did if you haven't opened it. */
	Supplies shared()
	{
		if (sharedStorage != null)
		{
			return sharedStorage;
		}
		for (Member member : team)
		{
			if (member.status != null && member.status.getShared() != null)
			{
				return member.status.getShared();
			}
		}
		return null;
	}

	/** Overload doses you hold as sets of elder, twisted and kodai. */
	int splitHeld()
	{
		return held().splitOverloadDoses();
	}

	/** Doses that count towards what you need. */
	int have(Potion potion)
	{
		int have = held().doses(potion);
		if (potion == Potion.OVERLOAD && countSplit)
		{
			have += splitHeld();
		}
		return have;
	}

	int shortfall(Potion potion)
	{
		return Math.max(0, need.getOrDefault(potion, 0) - have(potion));
	}

	/** Whether the supply row for this potion applies right now. */
	boolean applies(Potion potion)
	{
		return potion.isSupply() && (!potion.isSoloOnly() || (solo && trackStamina));
	}

	/** "Xeric's aid 8 doses, Stamina 1 potion" for everything short, empty when nothing is. */
	String shortfalls()
	{
		StringBuilder text = new StringBuilder();
		for (Potion potion : Potion.values())
		{
			int shortfall = applies(potion) ? shortfall(potion) : 0;
			if (shortfall > 0)
			{
				text.append(text.length() == 0 ? "" : ", ").append(potion.getDisplayName()).append(' ')
					.append(units.format(shortfall)).append(' ').append(units.getWord());
			}
		}
		return text.toString();
	}
}

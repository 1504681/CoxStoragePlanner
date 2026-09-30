package com.coxstorageplanner;

import java.util.Arrays;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Sent to the party when the doses you carry or have stored change. */
public class CoxStorageMessage extends PartyMemberMessage
{
	/** Doses in the inventory. */
	private int[] carried;
	/** Private storage, null if they haven't opened it this raid. */
	private int[] stored;
	/** Shared storage as this member last saw it, null if they haven't opened it this raid. */
	private int[] shared;

	public CoxStorageMessage()
	{
	}

	public CoxStorageMessage(int[] carried, int[] stored, int[] shared)
	{
		this.carried = carried;
		this.stored = stored;
		this.shared = shared;
	}

	public int[] getCarried()
	{
		return carried;
	}

	public int[] getStored()
	{
		return stored;
	}

	public int[] getShared()
	{
		return shared;
	}

	public boolean sameContent(CoxStorageMessage other)
	{
		return other != null
			&& Arrays.equals(carried, other.carried)
			&& Arrays.equals(stored, other.stored)
			&& Arrays.equals(shared, other.shared);
	}
}

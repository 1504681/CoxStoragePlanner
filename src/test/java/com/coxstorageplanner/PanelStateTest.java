package com.coxstorageplanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class PanelStateTest
{
	private static Supplies overloads(int doses)
	{
		return Supplies.of(new int[]{doses});
	}

	@Test
	public void shortfallCountsInventoryAndPrivateStorage()
	{
		PanelState state = new PanelState();
		state.need.put(Potion.OVERLOAD, 12);
		state.inventory = overloads(4);
		assertEquals(8, state.shortfall(Potion.OVERLOAD));
		state.privateStorage = overloads(4);
		assertEquals(4, state.shortfall(Potion.OVERLOAD));
	}

	@Test
	public void sharedOnlyCountsWhenAsked()
	{
		PanelState state = new PanelState();
		state.need.put(Potion.OVERLOAD, 12);
		state.sharedStorage = overloads(8);
		assertEquals(12, state.shortfall(Potion.OVERLOAD));
		state.countShared = true;
		assertEquals(4, state.shortfall(Potion.OVERLOAD));
	}

	@Test
	public void splitOverloadsCountAsOverload()
	{
		PanelState state = new PanelState();
		state.need.put(Potion.OVERLOAD, 12);
		state.countSplit = true;
		state.inventory = overloads(4);
		// elder in the inventory, twisted and kodai in private storage
		state.inventory = state.inventory.plus(Supplies.count(new int[]{20924}, new int[]{1}));
		state.privateStorage = Supplies.count(new int[]{20936, 20948}, new int[]{1, 1});
		assertEquals(8, state.have(Potion.OVERLOAD));
		assertEquals(4, state.shortfall(Potion.OVERLOAD));

		state.countSplit = false;
		assertEquals(4, state.have(Potion.OVERLOAD));
	}

	@Test
	public void neverNegative()
	{
		PanelState state = new PanelState();
		state.need.put(Potion.OVERLOAD, 4);
		state.inventory = overloads(40);
		assertEquals(0, state.shortfall(Potion.OVERLOAD));
		assertEquals(0, state.shortfall(Potion.KODAI));
	}

	@Test
	public void partyMessageWithoutStoragesIsFine()
	{
		MemberSupplies status = MemberSupplies.from(new CoxStorageMessage(new int[]{8, 12}, null, null));
		assertEquals(12, status.getCarried().doses(Potion.XERICS_AID));
		assertEquals(12, status.getHeld().doses(Potion.XERICS_AID));
		assertNull(status.getStored());
		assertNull(status.getShared());
		assertEquals(Supplies.EMPTY, MemberSupplies.from(new CoxStorageMessage()).getCarried());
	}

	@Test
	public void sharedStorageComesFromAPartyMemberWhenYouHaveNotOpenedIt()
	{
		PanelState state = new PanelState();
		assertNull(state.shared());
		PanelState.Member bob = new PanelState.Member();
		bob.name = "Bob";
		bob.status = new MemberSupplies(Supplies.EMPTY, null, overloads(8));
		state.team.add(bob);
		assertEquals(8, state.shared().doses(Potion.OVERLOAD));
		state.sharedStorage = overloads(4);
		assertEquals(4, state.shared().doses(Potion.OVERLOAD));
	}
}

package com.coxstorageplanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class PotionTest
{
	@Test
	public void raidPotionsHaveTwelveVariants()
	{
		assertEquals(1, Potion.OVERLOAD.doses(20985));
		assertEquals(4, Potion.OVERLOAD.doses(20988));
		assertEquals(4, Potion.OVERLOAD.doses(20996));
		assertEquals(20996, Potion.OVERLOAD.getIconItemId());
		assertEquals(0, Potion.OVERLOAD.doses(20997));
		assertEquals(Potion.XERICS_AID, Potion.of(20973));
		assertNull(Potion.of(4151));
	}

	@Test
	public void partyMessagesKeepTheLayoutOfTheFirstRelease()
	{
		// 1.0.0 had a potion between prayer enhance and elder; the gap stays so both versions read each other
		Supplies held = Supplies.count(new int[]{20996, 20984, 20916}, new int[]{1, 2, 1});
		int[] wire = held.toWire();
		assertEquals(Potion.values().length + 1, wire.length);
		assertEquals(4, wire[0]);
		assertEquals(8, wire[1]);
		assertEquals(0, wire[4]);
		assertEquals(4, wire[5]);
		assertEquals(held, Supplies.fromWire(wire));
		assertEquals(Supplies.EMPTY, Supplies.fromWire(null));
		assertEquals(8, Supplies.fromWire(new int[]{0, 8}).doses(Potion.XERICS_AID));
	}
}

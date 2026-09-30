package com.coxstorageplanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class NeedsTest
{
	@Test
	public void defaultsForNeverStored()
	{
		Needs needs = Needs.defaults();
		assertEquals(24, needs.get(Potion.XERICS_AID));
		assertEquals(0, needs.get(Potion.STAMINA));
		assertEquals(4, Needs.soloDefaults().get(Potion.STAMINA));
		assertEquals(needs.encode(), Needs.parse("", Needs.defaults()).encode());
		assertEquals(needs.encode(), Needs.parse(null, Needs.defaults()).encode());
	}

	@Test
	public void emptyIsNotTheSameAsNeverSet()
	{
		Needs needs = Needs.defaults();
		for (Potion potion : Potion.values())
		{
			needs.set(potion, 0);
		}
		assertEquals("none", needs.encode());
		assertEquals(0, Needs.parse(needs.encode(), Needs.defaults()).get(Potion.OVERLOAD));
	}

	@Test
	public void survivesTheConfigRoundTripAndJunk()
	{
		Needs needs = new Needs();
		needs.set(Potion.REVITALISATION, 3);
		needs.set(Potion.OVERLOAD, 500);
		Needs loaded = Needs.parse(needs.encode() + ",BEER:1,OVERLOAD,x", Needs.defaults());
		assertEquals(3, loaded.get(Potion.REVITALISATION));
		assertEquals(Needs.MAX_DOSES, loaded.get(Potion.OVERLOAD));
		assertEquals(needs.encode(), loaded.encode());
	}

	@Test
	public void readsThePerRoomFormOfTheFirstVersion()
	{
		Needs loaded = Needs.parse("TEKTON:OVERLOAD:1,OLM:OVERLOAD:4,OLM:XERICS_AID:12", Needs.defaults());
		assertEquals(5, loaded.get(Potion.OVERLOAD));
		assertEquals(12, loaded.get(Potion.XERICS_AID));
	}

	@Test
	public void reportsWhetherAnythingChanged()
	{
		Needs needs = new Needs();
		assertFalse(needs.set(Potion.OVERLOAD, 0));
		assertTrue(needs.set(Potion.OVERLOAD, 2));
		assertFalse(needs.set(Potion.OVERLOAD, 2));
		needs.set(Potion.OVERLOAD, -5);
		assertEquals(0, needs.get(Potion.OVERLOAD));
	}
}

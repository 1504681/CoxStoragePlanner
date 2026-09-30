package com.coxstorageplanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class SuppliesTest
{
	@Test
	public void dosesFollowTheItemId()
	{
		// overload (-)(1), overload (4), overload (+)(1), overload (+)(4)
		assertEquals(1, Potion.OVERLOAD.doses(20985));
		assertEquals(4, Potion.OVERLOAD.doses(20992));
		assertEquals(1, Potion.OVERLOAD.doses(20993));
		assertEquals(4, Potion.OVERLOAD.doses(20996));
		assertEquals(0, Potion.OVERLOAD.doses(20997));
		assertEquals(0, Potion.OVERLOAD.doses(20984));
	}

	@Test
	public void everyIdBelongsToOnePotion()
	{
		assertEquals(Potion.XERICS_AID, Potion.of(20984));
		assertEquals(Potion.REVITALISATION, Potion.of(20957));
		assertEquals(Potion.PRAYER_ENHANCE, Potion.of(20969));
		assertEquals(Potion.ELDER, Potion.of(20921));
		assertEquals(Potion.TWISTED, Potion.of(20933));
		assertEquals(Potion.KODAI, Potion.of(20945));
		// the nmz overload is a different potion
		assertNull(Potion.of(11730));
		assertNull(Potion.of(-1));
	}

	@Test
	public void countsDosesAcrossAContainer()
	{
		// xeric's aid (+)(4), xeric's aid (+)(2), overload (+)(3), a shark, an empty slot
		Supplies supplies = Supplies.count(new int[]{20984, 20982, 20995, 385, -1}, new int[]{1, 1, 1, 1, 0});
		assertEquals(6, supplies.doses(Potion.XERICS_AID));
		assertEquals(3, supplies.doses(Potion.OVERLOAD));
		assertEquals(0, supplies.doses(Potion.REVITALISATION));
	}

	@Test
	public void aSplitOverloadIsOnlyAsBigAsItsSmallestPart()
	{
		// elder (+)(4), twisted (+)(4), kodai (+)(3)
		Supplies set = Supplies.count(new int[]{20924, 20936, 20947}, new int[]{1, 1, 1});
		assertEquals(3, set.splitOverloadDoses());
		// no kodai, no overload
		assertEquals(0, Supplies.count(new int[]{20924, 20936}, new int[]{1, 1}).splitOverloadDoses());
		assertNull(Potion.of(-1));
		assertEquals(0, Potion.SPLIT_OVERLOAD.doses(-1));
	}

	@Test
	public void addsUp()
	{
		Supplies a = Supplies.count(new int[]{20996}, new int[]{1});
		Supplies b = Supplies.count(new int[]{20994, 20960}, new int[]{1, 1});
		assertEquals(6, a.plus(b).doses(Potion.OVERLOAD));
		assertEquals(4, a.plus(b).doses(Potion.REVITALISATION));
	}

	@Test
	public void toleratesShortOrHostileArrays()
	{
		assertEquals(Supplies.EMPTY, Supplies.of(null));
		assertEquals(5, Supplies.of(new int[]{5}).doses(Potion.OVERLOAD));
		assertEquals(0, Supplies.of(new int[]{-5}).doses(Potion.OVERLOAD));
		assertEquals(0, Supplies.of(new int[40]).doses(Potion.KODAI));
	}
}

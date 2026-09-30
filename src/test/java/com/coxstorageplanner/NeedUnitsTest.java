package com.coxstorageplanner;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class NeedUnitsTest
{
	@Test
	public void formatsDosesAsPotions()
	{
		assertEquals("6", NeedUnits.POTIONS.format(24));
		assertEquals("2.25", NeedUnits.POTIONS.format(9));
		assertEquals("0.5", NeedUnits.POTIONS.format(2));
		assertEquals("0", NeedUnits.POTIONS.format(0));
		assertEquals("9", NeedUnits.DOSES.format(9));
	}

	@Test
	public void parsesWhatItFormats()
	{
		assertEquals(24, NeedUnits.POTIONS.parse("6"));
		assertEquals(9, NeedUnits.POTIONS.parse(" 2.25 "));
		assertEquals(6, NeedUnits.POTIONS.parse("1.5"));
		assertEquals(9, NeedUnits.DOSES.parse("9"));
		assertEquals(0, NeedUnits.DOSES.parse("six"));
		assertEquals(0, NeedUnits.POTIONS.parse(""));
	}
}

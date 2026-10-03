package com.coxstorageplanner;

import java.awt.Color;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChestGlowBlendTest
{
	@Test
	public void greenToRedGoesByYellowAndOrange()
	{
		Color green = new Color(30, 220, 60, 255);
		Color red = new Color(240, 40, 40, 255);
		float[] second = hsb(ChestItemOverlay.blend(green, red, ChestItemOverlay.step(1, 4)));
		float[] third = hsb(ChestItemOverlay.blend(green, red, ChestItemOverlay.step(2, 4)));
		// hue in degrees: green 120-ish, yellow 60, orange 30, red 0
		assertTrue(second[0] * 360 > 55 && second[0] * 360 < 70);
		assertTrue(third[0] * 360 > 25 && third[0] * 360 < 40);
		assertEquals(0f, ChestItemOverlay.step(0, 4), 0f);
		assertEquals(1f, ChestItemOverlay.step(3, 4), 0f);
		// two left: green and yellow, not green and red
		assertEquals(0.5f, ChestItemOverlay.step(1, 4), 0f);
		assertEquals(1f, ChestItemOverlay.step(1, 2), 0f);
		assertEquals(green, ChestItemOverlay.blend(green, red, 0));
		assertEquals(red, ChestItemOverlay.blend(green, red, 1));
		// no brown in between
		Color mid = ChestItemOverlay.blend(green, red, 0.5f);
		assertTrue(hsb(mid)[2] > 0.8);
	}

	@Test
	public void greyIsMetInAStraightLine()
	{
		Color grey = new Color(128, 128, 128, 100);
		Color red = new Color(240, 40, 40, 255);
		Color mid = ChestItemOverlay.blend(red, grey, 0.5f);
		assertEquals(184, mid.getRed());
		assertEquals(178, mid.getAlpha());
	}

	private static float[] hsb(Color c)
	{
		return Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
	}
}

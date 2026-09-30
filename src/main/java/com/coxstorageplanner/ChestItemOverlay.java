package com.coxstorageplanner;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the items a chest plan still wants moved: in the side inventory what goes in,
 * in the storage what comes out, and gear to put on in its own colour before either. The next one
 * of an ordered plan pulses; with the next three lit, each carries a numbered orb that shrinks the
 * further down the order it is.
 */
class ChestItemOverlay extends WidgetItemOverlay
{
	private final CoxStoragePlannerPlugin plugin;
	private final CoxStoragePlannerConfig config;
	private final ItemManager itemManager;

	@Inject
	ChestItemOverlay(CoxStoragePlannerPlugin plugin, CoxStoragePlannerConfig config, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInterfaces(InterfaceID.RAIDS_STORAGE_PRIVATE, InterfaceID.RAIDS_STORAGE_SHARED, InterfaceID.RAIDS_STORAGE_SIDE);
	}

	/** Orb diameters for the next withdrawal, the one after and the one after that. The next one covers the item. */
	private static final int[] ORB_SIZES = {28, 15, 10};

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.chestGlow())
		{
			return;
		}
		ChestProgress progress = plugin.getOpenChest();
		if (progress == null)
		{
			return;
		}
		String name = plugin.itemName(itemId);
		int group = widgetItem.getWidget().getId() >>> 16;
		Color color;
		int order = 0;
		int orb = 0;
		boolean pulse;
		if (progress.highlightsWear(name))
		{
			// gear to put on, wherever it is
			color = config.chestWearColor();
			pulse = true;
		}
		else if (group == InterfaceID.RAIDS_STORAGE_SIDE)
		{
			if (!progress.highlightsDeposit(name))
			{
				return;
			}
			color = config.chestGlowColor();
			pulse = true;
		}
		else
		{
			ChestGlow mode = config.chestOrderedGlow();
			ChestProgress.Step step = progress.highlightsWithdraw(name, mode.getSteps());
			if (step == null)
			{
				return;
			}
			int rank = progress.rank(step);
			pulse = !progress.plan.isOrdered() || rank == 0;
			color = config.chestGlowColor();
			if (progress.plan.isOrdered())
			{
				order = step.order;
				switch (mode)
				{
					case NEXT_THREE:
						// the next one is big and bright, the ones behind it smaller and fainter
						orb = ORB_SIZES[Math.min(rank, ORB_SIZES.length - 1)];
						color = fade(color, rank == 0 ? 1f : rank == 1 ? 0.6f : 0.4f);
						break;
					case GRADIENT:
						orb = ORB_SIZES[1];
						if (progress.withdrawals.size() > 1)
						{
							color = blend(config.chestGlowColor(), config.chestGlowLastColor(),
								(step.order - 1) / (float) (progress.withdrawals.size() - 1));
						}
						break;
					default:
						orb = ORB_SIZES[1];
				}
			}
		}
		if (pulse && config.chestGlowPulse())
		{
			// a slow breathe between half and full strength
			double phase = (System.currentTimeMillis() % 1200) / 1200.0 * 2 * Math.PI;
			color = fade(color, (float) (0.75 + 0.25 * Math.sin(phase)));
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), color);
		graphics.drawImage(outline, bounds.x, bounds.y, null);
		if (order > 0)
		{
			drawOrb(graphics, bounds, orb, color, order);
		}
	}

	/**
	 * A filled circle with the step number in it: the biggest sits over the middle of the item so the
	 * next click can't be missed, the smaller ones in the top left corner.
	 */
	private static void drawOrb(Graphics2D graphics, Rectangle bounds, int size, Color color, int order)
	{
		Object aa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		boolean centered = size >= ORB_SIZES[0];
		int x = centered ? bounds.x + (bounds.width - size) / 2 : bounds.x - 1;
		int y = centered ? bounds.y + (bounds.height - size) / 2 : bounds.y - 1;
		int ring = centered ? 2 : 1;
		graphics.setColor(new Color(0, 0, 0, Math.min(255, color.getAlpha())));
		graphics.fillOval(x - ring, y - ring, size + 2 * ring, size + 2 * ring);
		graphics.setColor(color);
		graphics.fillOval(x, y, size, size);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);

		Font font = centered ? FontManager.getRunescapeBoldFont().deriveFont(20f) : FontManager.getRunescapeSmallFont();
		graphics.setFont(font);
		String text = String.valueOf(order);
		FontMetrics metrics = graphics.getFontMetrics();
		int tx = x + (size - metrics.stringWidth(text)) / 2;
		int ty = y + (size + metrics.getAscent() - metrics.getDescent()) / 2;
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, tx + 1, ty + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, tx, ty);
	}

	private static Color fade(Color color, float strength)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * strength));
	}

	private static Color blend(Color a, Color b, float t)
	{
		return new Color(
			Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
			Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
			Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
			Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
	}
}

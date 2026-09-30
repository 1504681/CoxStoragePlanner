package com.coxstorageplanner;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the items a chest plan still wants moved: in the side inventory what goes in,
 * in the storage what comes out. The next one of an ordered plan pulses.
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
		String number = null;
		boolean pulse;
		if (group == InterfaceID.RAIDS_STORAGE_SIDE)
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
			boolean nextOnly = config.chestOrderedGlow() == ChestGlow.NEXT_ONLY;
			ChestProgress.Step step = progress.highlightsWithdraw(name, nextOnly);
			if (step == null)
			{
				return;
			}
			ChestProgress.Step next = progress.next();
			pulse = !progress.plan.isOrdered() || step == next;
			color = config.chestGlowColor();
			if (progress.plan.isOrdered())
			{
				number = String.valueOf(step.order);
				if (!nextOnly && progress.withdrawals.size() > 1)
				{
					color = blend(config.chestGlowColor(), config.chestGlowLastColor(),
						(step.order - 1) / (float) (progress.withdrawals.size() - 1));
				}
			}
		}
		if (pulse && config.chestGlowPulse())
		{
			// a slow breathe between half and full strength
			double phase = (System.currentTimeMillis() % 1200) / 1200.0 * 2 * Math.PI;
			int alpha = (int) (color.getAlpha() * (0.75 + 0.25 * Math.sin(phase)));
			color = new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
		}
		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), color);
		graphics.drawImage(outline, bounds.x, bounds.y, null);
		if (number != null)
		{
			graphics.setFont(FontManager.getRunescapeSmallFont());
			graphics.setColor(Color.BLACK);
			graphics.drawString(number, bounds.x + 2, bounds.y + 11);
			graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue()));
			graphics.drawString(number, bounds.x + 1, bounds.y + 10);
		}
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

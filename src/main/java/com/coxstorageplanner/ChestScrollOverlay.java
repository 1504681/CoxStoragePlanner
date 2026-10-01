package com.coxstorageplanner;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Lights the storage's scroll arrow, up or down, when the item to click next is scrolled out of
 * view: gear to put on, or the next withdrawal.
 */
class ChestScrollOverlay extends Overlay
{
	private final Client client;
	private final CoxStoragePlannerPlugin plugin;
	private final CoxStoragePlannerConfig config;

	@Inject
	ChestScrollOverlay(Client client, CoxStoragePlannerPlugin plugin, CoxStoragePlannerConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.chestScrollHint())
		{
			return null;
		}
		ChestProgress progress = plugin.getOpenChest();
		int group = plugin.getOpenStorage();
		if (progress == null || group == 0)
		{
			return null;
		}
		boolean shared = group == InterfaceID.RAIDS_STORAGE_SHARED;
		Widget items = client.getWidget(shared ? InterfaceID.RaidsStorageShared.ITEMS : InterfaceID.RaidsStoragePrivate.ITEMS);
		Widget bar = client.getWidget(shared ? InterfaceID.RaidsStorageShared.SCROLLBAR : InterfaceID.RaidsStoragePrivate.SCROLLBAR);
		if (items == null || bar == null || items.isHidden() || items.getScrollHeight() <= items.getHeight())
		{
			return null;
		}
		Widget[] slots = items.getDynamicChildren();
		if (slots == null)
		{
			return null;
		}
		int top = items.getScrollY();
		int bottom = top + items.getHeight();
		int direction = 0;
		for (Widget slot : slots)
		{
			int itemId = slot == null ? -1 : slot.getItemId();
			if (itemId <= 0 || !progress.wantsNext(plugin.itemName(itemId)))
			{
				continue;
			}
			int middle = slot.getRelativeY() + slot.getHeight() / 2;
			if (middle >= top && middle <= bottom)
			{
				// one to click is in view already
				return null;
			}
			if (direction == 0)
			{
				direction = middle < top ? -1 : 1;
			}
		}
		Rectangle bounds = bar.getBounds();
		if (direction == 0 || bounds == null || bounds.width <= 0 || bounds.height < 2 * bounds.width)
		{
			return null;
		}
		// the arrows are the square ends of the bar
		int size = bounds.width;
		int y = direction < 0 ? bounds.y : bounds.y + bounds.height - size;
		Color color = config.chestGlowColor();
		double phase = (System.currentTimeMillis() % 800) / 800.0 * 2 * Math.PI;
		float strength = config.chestGlowPulse() ? (float) (0.65 + 0.35 * Math.sin(phase)) : 1f;
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(110 * strength)));
		graphics.fillRect(bounds.x, y, size, size);
		graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(255 * strength)));
		graphics.drawRect(bounds.x - 1, y - 1, size + 1, size + 1);
		graphics.drawRect(bounds.x - 2, y - 2, size + 3, size + 3);
		return null;
	}
}

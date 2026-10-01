package com.coxstorageplanner;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * An arrow from an inventory item to the slot it belongs in, for when the storage is full and the
 * plan can only be put right by dragging. Drawn over the storage's side inventory, or over the
 * inventory itself once the storage is shut.
 */
class ChestMoveOverlay extends Overlay
{
	private static final int HEAD = 9;

	private final Client client;
	private final CoxStoragePlannerPlugin plugin;
	private final CoxStoragePlannerConfig config;

	@Inject
	ChestMoveOverlay(Client client, CoxStoragePlannerPlugin plugin, CoxStoragePlannerConfig config)
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
		if (!config.chestGlow())
		{
			return null;
		}
		ChestProgress progress = plugin.progressHere();
		if (progress == null || progress.moveFrom() < 0)
		{
			return null;
		}
		Widget items = client.getWidget(plugin.getOpenStorage() != 0 ? InterfaceID.RaidsStorageSide.ITEMS : InterfaceID.Inventory.ITEMS);
		if (items == null || items.isHidden())
		{
			return null;
		}
		Widget from = items.getChild(progress.moveFrom());
		Widget to = items.getChild(progress.moveTo());
		// the widgets are the slots in order; if that one doesn't hold the item, they aren't, so draw nothing
		if (from == null || to == null || from.getItemId() <= 0 || !progress.moveName().equals(plugin.itemName(from.getItemId())))
		{
			return null;
		}
		Rectangle a = from.getBounds();
		Rectangle b = to.getBounds();
		if (a == null || b == null)
		{
			return null;
		}
		Color color = config.chestGlowColor();
		if (config.chestGlowPulse())
		{
			double phase = (System.currentTimeMillis() % 1200) / 1200.0 * 2 * Math.PI;
			color = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) Math.round(color.getAlpha() * (0.75 + 0.25 * Math.sin(phase))));
		}
		Object aa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		Stroke stroke = graphics.getStroke();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(2));
		graphics.drawRoundRect(a.x, a.y, a.width, a.height, 6, 6);
		graphics.drawRoundRect(b.x, b.y, b.width, b.height, 6, 6);

		double x1 = a.getCenterX();
		double y1 = a.getCenterY();
		double x2 = b.getCenterX();
		double y2 = b.getCenterY();
		double angle = Math.atan2(y2 - y1, x2 - x1);
		for (int pass = 0; pass < 2; pass++)
		{
			// black underneath so it reads over any item
			graphics.setColor(pass == 0 ? new Color(0, 0, 0, color.getAlpha()) : color);
			graphics.setStroke(new BasicStroke(pass == 0 ? 5 : 3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			graphics.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
			graphics.drawLine((int) x2, (int) y2, (int) (x2 - HEAD * Math.cos(angle - 0.5)), (int) (y2 - HEAD * Math.sin(angle - 0.5)));
			graphics.drawLine((int) x2, (int) y2, (int) (x2 - HEAD * Math.cos(angle + 0.5)), (int) (y2 - HEAD * Math.sin(angle + 0.5)));
		}
		graphics.setStroke(stroke);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
		return null;
	}
}

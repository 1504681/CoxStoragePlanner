package com.coxstorageplanner;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import javax.inject.Inject;
import net.runelite.api.GameObject;
import net.runelite.api.Point;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A mark over the storage unit in the room you're in: a "?" while its chest still has things to wear,
 * put in or take out, a green tick once the inventory says it's all done.
 */
class ChestMarkerOverlay extends Overlay
{
	private static final Color TODO = new Color(255, 220, 90);
	private static final Color DONE = new Color(90, 230, 90);
	private static final int SIZE = 22;
	private static final int HEIGHT = 220;

	private final CoxStoragePlannerPlugin plugin;
	private final CoxStoragePlannerConfig config;

	@Inject
	ChestMarkerOverlay(CoxStoragePlannerPlugin plugin, CoxStoragePlannerConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.chestMarkers())
		{
			return null;
		}
		for (GameObject storage : plugin.getStorageUnits())
		{
			Boolean done = plugin.chestDoneAt(storage);
			if (done == null)
			{
				continue;
			}
			Point point = storage.getCanvasLocation(HEIGHT);
			if (point == null)
			{
				continue;
			}
			Object aa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (done)
			{
				tick(graphics, point.getX(), point.getY());
			}
			else
			{
				question(graphics, point.getX(), point.getY());
			}
			graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
		}
		return null;
	}

	private static void question(Graphics2D graphics, int x, int y)
	{
		Font font = FontManager.getRunescapeBoldFont().deriveFont((float) SIZE);
		graphics.setFont(font);
		FontMetrics metrics = graphics.getFontMetrics();
		int tx = x - metrics.stringWidth("?") / 2;
		int ty = y + metrics.getAscent() / 2;
		graphics.setColor(Color.BLACK);
		graphics.drawString("?", tx + 1, ty + 1);
		graphics.setColor(TODO);
		graphics.drawString("?", tx, ty);
	}

	private static void tick(Graphics2D graphics, int x, int y)
	{
		Stroke stroke = graphics.getStroke();
		int[] xs = {x - SIZE / 2, x - SIZE / 6, x + SIZE / 2};
		int[] ys = {y, y + SIZE / 3, y - SIZE / 3};
		graphics.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(Color.BLACK);
		graphics.drawPolyline(xs, ys, 3);
		graphics.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		graphics.setColor(DONE);
		graphics.drawPolyline(xs, ys, 3);
		graphics.setStroke(stroke);
	}
}

package com.coxstorageplanner;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the items a chest plan still wants moved: in the side inventory what goes in,
 * in the storage what comes out, and gear to put on in its own colour before either. With an ordered
 * plan every click has its own number, so "Xeric's aid, 2" lights two of them as 17 and 18; the next
 * click pulses, and with the next four lit the orbs shrink and shift colour the further off they are.
 * A stack that wants more than one out shows "x5" in the slot's top right corner. A plan held to the
 * slot lights the inventory by slot, so only the items out of place, and only what the storage has room for.
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

	/** Orb diameters for the next withdrawal and the three after it. The next one covers the item. */
	private static final int[] ORB_SIZES = {28, 16, 12, 10};
	/** How much the colour fades for each of those, on top of its shift towards the end colour. */
	private static final float[] ORB_FADE = {1f, 0.9f, 0.8f, 0.7f};

	/** How much of each step has lit up so far this frame, so "Xeric's aid, 2" lights two and not the whole row. */
	private final Map<ChestProgress.Step, Integer> lit = new IdentityHashMap<>();
	/** The withdrawals of a plan held to the slot that an item lit up for so far this frame. */
	private final Set<ChestProgress.Click> taken = Collections.newSetFromMap(new IdentityHashMap<>());

	@Override
	public Dimension render(Graphics2D graphics)
	{
		lit.clear();
		taken.clear();
		return super.render(graphics);
	}

	/** Whether this item is beyond the step's remaining count; counts it otherwise. */
	private boolean enough(ChestProgress.Step step, WidgetItem widgetItem)
	{
		int already = lit.getOrDefault(step, 0);
		if (already >= step.remaining)
		{
			return true;
		}
		lit.put(step, already + Math.max(1, widgetItem.getQuantity()));
		return false;
	}

	/** A storage item's place in the withdrawal: its step, its click's number in the plan, and how many clicks come first. */
	static final class Click
	{
		final ChestProgress.Step step;
		final int number;
		final int rank;

		private Click(ChestProgress.Step step, int number, int rank)
		{
			this.step = step;
			this.number = number;
			this.rank = rank;
		}
	}

	/**
	 * The withdrawal a storage item lights up for this frame, null for none. Each item takes the next
	 * click of the first step that still wants one, so "Xeric's aid, 2" lights two aids as two clicks
	 * and leaves the rest of the row dark. With an ordered plan only the next {@code limit} clicks light.
	 */
	Click withdrawal(ChestProgress progress, String name, int quantity, int limit)
	{
		if (progress.exact())
		{
			// the clicks that can be made now, each lighting one item; a pile in the storage takes as many as it holds
			List<ChestProgress.Click> queue = progress.queue();
			Click first = null;
			int used = 0;
			for (int rank = 0; rank < queue.size() && rank < limit && used < Math.max(1, quantity); rank++)
			{
				ChestProgress.Click click = queue.get(rank);
				if (taken.contains(click) || !click.step.line.matches(name) || !progress.fullest(click.step.line, name))
				{
					continue;
				}
				taken.add(click);
				used += click.step.stack ? quantity : 1;
				if (first == null)
				{
					first = new Click(click.step, click.number, rank);
				}
			}
			return first;
		}
		boolean ordered = progress.plan.isOrdered();
		for (ChestProgress.Step step : progress.withdrawSteps(name, limit))
		{
			int already = lit.getOrDefault(step, 0);
			// a stack is one click however many come out of it
			int ahead = step.stack ? 0 : already;
			if (already >= step.remaining || (ordered && step.rank + ahead >= limit))
			{
				continue;
			}
			lit.put(step, already + Math.max(1, quantity));
			return new Click(step, step.click + ahead, step.rank + ahead);
		}
		return null;
	}

	/** Whether this is the first item lit for the step, which is the one that carries its "xN". */
	private boolean firstLit(ChestProgress.Step step, WidgetItem widgetItem)
	{
		return lit.getOrDefault(step, 0) == Math.max(1, widgetItem.getQuantity());
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
		int order = 0;
		int orb = 0;
		boolean pulse;
		// whether the slot says how many, as "x5" in its corner
		boolean counted;
		ChestProgress.Step step;
		ChestProgress.Step wear = progress.wearStep(name);
		if (wear != null)
		{
			// gear to put on, wherever it is
			if (enough(wear, widgetItem))
			{
				return;
			}
			step = wear;
			counted = firstLit(step, widgetItem);
			color = config.chestWearColor();
			pulse = true;
		}
		else if (group == InterfaceID.RAIDS_STORAGE_SIDE && progress.exact())
		{
			// the side inventory's widgets are its slots, in order
			int slot = widgetItem.getWidget().getIndex();
			if (!progress.depositsSlot(slot, name))
			{
				return;
			}
			step = progress.depositStepAt(slot, name);
			counted = widgetItem.getQuantity() > 1;
			color = config.chestGlowColor();
			pulse = true;
		}
		else if (group == InterfaceID.RAIDS_STORAGE_SIDE)
		{
			step = progress.depositStep(name);
			if (step == null ? !progress.highlightsDeposit(name) : enough(step, widgetItem))
			{
				return;
			}
			counted = step != null && firstLit(step, widgetItem);
			color = config.chestGlowColor();
			pulse = true;
		}
		else
		{
			ChestGlow mode = config.chestOrderedGlow();
			boolean ordered = progress.plan.isOrdered();
			Click click = withdrawal(progress, name, widgetItem.getQuantity(), mode.getSteps());
			if (click == null)
			{
				return;
			}
			step = click.step;
			counted = progress.exact() ? step.stack : firstLit(step, widgetItem) && (!ordered || step.stack);
			int rank = click.rank;
			pulse = !ordered || rank == 0;
			color = config.chestGlowColor();
			if (ordered)
			{
				order = click.number;
				int near = Math.min(rank, ORB_SIZES.length - 1);
				switch (mode)
				{
					case NEXT_FOUR:
						// the next click is big and in the first colour, the ones behind it smaller and nearer the last
						orb = ORB_SIZES[near];
						color = fade(blend(color, config.chestGlowLastColor(), near / (float) (ORB_SIZES.length - 1)), ORB_FADE[near]);
						break;
					case GRADIENT:
						orb = ORB_SIZES[1];
						if (progress.clicks > 1)
						{
							color = blend(color, config.chestGlowLastColor(), (order - 1) / (float) (progress.clicks - 1));
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
		// numbered clicks already say how many; a stack, a deposit or gear says it here
		if (counted && step != null && step.remaining > 1 && step.remaining != Integer.MAX_VALUE)
		{
			drawCount(graphics, bounds, step.remaining, color);
		}
	}

	/** "x5" in the slot's top right corner, small, outlined so it reads over the orb's edge. */
	private static void drawCount(Graphics2D graphics, Rectangle bounds, int count, Color color)
	{
		graphics.setFont(FontManager.getRunescapeSmallFont());
		String text = "x" + count;
		FontMetrics metrics = graphics.getFontMetrics();
		int tx = bounds.x + bounds.width - metrics.stringWidth(text);
		int ty = bounds.y + metrics.getAscent() - 2;
		graphics.setColor(Color.BLACK);
		for (int dx = -1; dx <= 1; dx++)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				graphics.drawString(text, tx + dx, ty + dy);
			}
		}
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, tx, ty);
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

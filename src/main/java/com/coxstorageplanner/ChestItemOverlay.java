package com.coxstorageplanner;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Outlines the items a chest plan still wants moved: in the side inventory what goes in,
 * in the storage what comes out, and gear to put on in its own colour before either. With an ordered
 * plan the clicks to make are numbered from the next one: 1, 2, 3, whatever is done already. The next
 * click pulses, and with the next few lit the orbs shrink the further off they are, while the colour
 * says how soon a click comes, green for the next one through yellow and orange to red for the fourth,
 * each step its own colour however many are lit; a setting joins them up with lines, in order.
 * Clicking any of several identical items in the storage takes the first of them and leaves the rest
 * where they are, so only the last of a kind lights: "Xeric's aid, 3" is that one aid clicked three
 * times, with "x3" in the slot's top right corner. A plan held to the slot lights the inventory by slot,
 * so only the items out of place, and never more than the storage has room for.
 */
class ChestItemOverlay extends WidgetItemOverlay
{
	private final Client client;
	private final CoxStoragePlannerPlugin plugin;
	private final CoxStoragePlannerConfig config;
	private final ItemManager itemManager;

	@Inject
	ChestItemOverlay(Client client, CoxStoragePlannerPlugin plugin, CoxStoragePlannerConfig config, ItemManager itemManager)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.itemManager = itemManager;
		showOnInterfaces(InterfaceID.RAIDS_STORAGE_PRIVATE, InterfaceID.RAIDS_STORAGE_SHARED, InterfaceID.RAIDS_STORAGE_SIDE);
	}

	/** Orb diameter of the next withdrawal, which covers the item, and of the nearest and furthest ones after it. */
	private static final int ORB_NEXT = 28;
	private static final int ORB_NEAR = 20;
	private static final int ORB_FAR = 10;

	/** How much of each step has lit up so far this frame, so "Xeric's aid, 2" lights two and not the whole row. */
	private final Map<ChestProgress.Step, Integer> lit = new IdentityHashMap<>();
	/** The withdrawals of a plan held to the slot that an item lit up for so far this frame. */
	private final Set<ChestProgress.Click> taken = Collections.newSetFromMap(new IdentityHashMap<>());
	/** Per item in the open storage: the widget index of the last one of it, and how many of it there are. */
	private final Map<Integer, int[]> twins = new HashMap<>();
	/** The widget those indices are children of. */
	private int twinsParent;
	/** Deposits lit so far this frame, by item name. */
	private final Map<String, Integer> named = new HashMap<>();
	/** Deposits lit so far this frame that take a storage slot. */
	private int slotsLit;
	/** The orbs of the ordered withdrawals drawn this frame, by rank: centre x, centre y, radius; and their colours. */
	private final Map<Integer, int[]> orbs = new TreeMap<>();
	private final Map<Integer, Color> orbColors = new HashMap<>();

	@Override
	public Dimension render(Graphics2D graphics)
	{
		lit.clear();
		taken.clear();
		named.clear();
		slotsLit = 0;
		twins.clear();
		orbs.clear();
		orbColors.clear();
		int group = plugin.getOpenStorage();
		Widget items = group == 0 ? null : client.getWidget(group == InterfaceID.RAIDS_STORAGE_SHARED
			? InterfaceID.RaidsStorageShared.ITEMS : InterfaceID.RaidsStoragePrivate.ITEMS);
		Widget[] slots = items == null ? null : items.getDynamicChildren();
		twinsParent = items == null ? -1 : items.getId();
		for (int i = 0; slots != null && i < slots.length; i++)
		{
			Widget slot = slots[i];
			if (slot == null || slot.getItemId() <= 0 || slot.isHidden())
			{
				continue;
			}
			int[] twin = twins.computeIfAbsent(slot.getItemId(), id -> new int[]{-1, 0});
			twin[0] = Math.max(twin[0], slot.getIndex());
			twin[1] += Math.max(1, slot.getItemQuantity());
		}
		Shape clip = graphics.getClip();
		super.render(graphics);
		graphics.setClip(clip);
		if (config.chestGlowPath() && orbs.size() > 1)
		{
			drawPath(graphics);
		}
		return null;
	}

	/** Joins the orbs up in click order, orb edge to orb edge, each line in the colour of the click it leaves. */
	private void drawPath(Graphics2D graphics)
	{
		Object aa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		Stroke stroke = graphics.getStroke();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int[] from = null;
		Color color = null;
		for (Map.Entry<Integer, int[]> e : orbs.entrySet())
		{
			int[] to = e.getValue();
			if (from != null)
			{
				double dx = to[0] - from[0];
				double dy = to[1] - from[1];
				double length = Math.hypot(dx, dy);
				if (length > from[2] + to[2] + 4)
				{
					double ux = dx / length;
					double uy = dy / length;
					double x1 = from[0] + ux * (from[2] + 1);
					double y1 = from[1] + uy * (from[2] + 1);
					double x2 = to[0] - ux * (to[2] + 1);
					double y2 = to[1] - uy * (to[2] + 1);
					// an arrowhead at the far end, so the line says which way
					Path2D.Double line = new Path2D.Double();
					line.moveTo(x1, y1);
					line.lineTo(x2, y2);
					line.moveTo(x2 - ux * 7 + uy * 4, y2 - uy * 7 - ux * 4);
					line.lineTo(x2, y2);
					line.lineTo(x2 - ux * 7 - uy * 4, y2 - uy * 7 + ux * 4);
					graphics.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					graphics.setColor(new Color(0, 0, 0, Math.min(255, color.getAlpha())));
					graphics.draw(line);
					graphics.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					graphics.setColor(color);
					graphics.draw(line);
				}
			}
			from = to;
			color = orbColors.get(e.getKey());
		}
		graphics.setStroke(stroke);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
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

	/**
	 * A storage item's place in the withdrawal: its step, its first click's place in the whole list
	 * (for the colour), how many clicks come first (its number, less one), and how many it takes.
	 */
	static final class Click
	{
		final ChestProgress.Step step;
		final int number;
		final int rank;
		final int count;

		private Click(ChestProgress.Step step, int number, int rank, int count)
		{
			this.step = step;
			this.number = number;
			this.rank = rank;
			this.count = count;
		}
	}

	/**
	 * The withdrawal a storage item lights up for this frame, null for none. An item takes the next
	 * clicks that want it, as many as there are of it, so "Xeric's aid, 2" lights two single aids as
	 * two clicks, or one that stands for several as both. With an ordered plan only the next
	 * {@code limit} clicks light, and an item only takes clicks that follow one another.
	 *
	 * @param quantity how many items this one stands for
	 */
	Click withdrawal(ChestProgress progress, String name, int quantity, int limit)
	{
		ChestProgress.Step step = null;
		int number = 0;
		int first = 0;
		int count = 0;
		int left = Math.max(1, quantity);
		if (progress.exact())
		{
			// the clicks that can be made now
			List<ChestProgress.Click> queue = progress.queue();
			for (int rank = 0; rank < queue.size() && rank < limit && left > 0; rank++)
			{
				ChestProgress.Click click = queue.get(rank);
				if (count > 0 && rank != first + count)
				{
					// something else is clicked in between
					break;
				}
				if (taken.contains(click) || !click.step.line.matches(name) || !progress.fullest(click.step.line, name))
				{
					continue;
				}
				taken.add(click);
				left -= click.step.stack ? left : 1;
				if (count++ == 0)
				{
					step = click.step;
					number = click.number;
					first = rank;
				}
			}
			return count == 0 ? null : new Click(step, number, first, count);
		}
		boolean ordered = progress.plan.isOrdered();
		for (ChestProgress.Step next : progress.withdrawSteps(name, limit))
		{
			int already = lit.getOrDefault(next, 0);
			// a stack is one click however many come out of it
			int ahead = next.stack ? 0 : already;
			if (left <= 0 || already >= next.remaining || (ordered && next.rank + ahead >= limit))
			{
				continue;
			}
			if (ordered && count > 0 && next.click + ahead != number + count)
			{
				break;
			}
			int take = next.stack ? left : Math.min(left, next.remaining - already);
			if (ordered && !next.stack)
			{
				take = Math.min(take, limit - next.rank - ahead);
			}
			lit.put(next, already + take);
			left -= take;
			if (count == 0)
			{
				step = next;
				number = next.click + ahead;
				first = next.rank + ahead;
			}
			count += next.stack ? 1 : take;
		}
		return count == 0 ? null : new Click(step, number, first, count);
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
		// an ordered withdrawal's place among the clicks lit, for the lines between them
		int pathRank = -1;
		boolean pulse;
		// the "x5" in the slot's corner: how many, or how many clicks
		int times = 0;
		ChestProgress.Step wear = progress.wearStep(name);
		if (wear != null)
		{
			// gear to put on, wherever it is
			if (enough(wear, widgetItem))
			{
				return;
			}
			times = firstLit(wear, widgetItem) ? wear.remaining : 0;
			color = config.chestWearColor();
			pulse = true;
		}
		else if (group == InterfaceID.RAIDS_STORAGE_SIDE && progress.exact())
		{
			// the side inventory's widgets are its slots, in order; if this one isn't, go by the name
			int slot = widgetItem.getWidget().getIndex();
			int already = named.getOrDefault(name, 0);
			if (name.equals(progress.nameAt(slot)) ? !progress.depositsSlot(slot, name) : already >= progress.depositsNamed(name))
			{
				return;
			}
			named.put(name, already + 1);
			times = widgetItem.getQuantity() > 1 ? widgetItem.getQuantity() : 0;
			color = config.chestGlowColor();
			pulse = true;
		}
		else if (group == InterfaceID.RAIDS_STORAGE_SIDE)
		{
			ChestProgress.Step step = progress.depositStep(name);
			if (step == null ? !progress.highlightsDeposit(name) : lit.getOrDefault(step, 0) >= step.remaining)
			{
				return;
			}
			// no more than the storage has slots for
			if (!progress.freeDeposit(name) && slotsLit++ >= progress.free)
			{
				return;
			}
			if (step != null)
			{
				enough(step, widgetItem);
				times = firstLit(step, widgetItem) ? step.remaining : 0;
			}
			color = config.chestGlowColor();
			pulse = true;
		}
		else
		{
			int quantity = widgetItem.getQuantity();
			int[] twin = widgetItem.getWidget().getParentId() == twinsParent ? twins.get(itemId) : null;
			if (twin != null)
			{
				// a click on any of them takes the first and leaves the rest in place: the last one is the one to click
				if (widgetItem.getWidget().getIndex() != twin[0])
				{
					return;
				}
				quantity = twin[1];
			}
			ChestGlow mode = config.chestOrderedGlow();
			int limit = mode == ChestGlow.NEXT_FOUR ? Math.max(2, config.chestGlowCount()) : mode.getSteps();
			boolean ordered = progress.plan.isOrdered();
			Click click = withdrawal(progress, name, quantity, limit);
			if (click == null)
			{
				return;
			}
			times = click.step.stack ? click.step.remaining : click.count;
			int rank = click.rank;
			pulse = !ordered || rank == 0;
			color = ordered ? config.chestGlowFirstColor() : config.chestGlowColor();
			if (ordered)
			{
				// a click keeps its number while the ones before it get made: 1 2 3 4, then 2 3 4 5
				order = progress.number(click.number);
				pathRank = rank;
				// the colour says how soon: the next click's colour on it, the end colour on the last of the
				// clicks shown, each step its own colour, so two left are green and yellow, not green and red
				int span = mode == ChestGlow.NEXT_FOUR ? limit : Math.min(limit, progress.toClick());
				if (span > 1)
				{
					color = blend(color, config.chestGlowLastColor(), Math.min(1f, rank / (float) (span - 1)));
				}
				if (mode == ChestGlow.NEXT_FOUR)
				{
					// the next click is big, the ones behind it smaller and fainter the further off
					float far = rank == 0 || limit <= 2 ? 0 : (rank - 1) / (float) (limit - 2);
					orb = rank == 0 ? ORB_NEXT : Math.round(ORB_NEAR - (ORB_NEAR - ORB_FAR) * far);
					color = rank == 0 ? color : fade(color, 0.9f - 0.2f * far);
				}
				else
				{
					orb = ORB_NEAR;
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
		// thicker is the same outline again, shifted a pixel each way; half a pixel is one at half strength;
		// gear to put on gets a pixel more, so it stands out from what's merely to be moved
		double width = Math.max(1, Math.min(4, config.chestGlowWidth())) + (wear != null ? 1 : 0);
		int reach = (int) Math.ceil(width - 1);
		Composite composite = graphics.getComposite();
		for (int dx = -reach; dx <= reach; dx++)
		{
			for (int dy = -reach; dy <= reach; dy++)
			{
				float strength = (float) Math.min(1, width - Math.abs(dx) - Math.abs(dy));
				if (strength > 0)
				{
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, strength));
					graphics.drawImage(outline, bounds.x + dx, bounds.y + dy, null);
				}
			}
		}
		graphics.setComposite(composite);
		if (order > 0)
		{
			int[] center = drawOrb(graphics, bounds, orb, color, order);
			if (pathRank >= 0 && !orbs.containsKey(pathRank))
			{
				orbs.put(pathRank, center);
				orbColors.put(pathRank, color);
			}
		}
		if (times > 1 && times != Integer.MAX_VALUE)
		{
			drawCount(graphics, bounds, times, color);
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
	private static int[] drawOrb(Graphics2D graphics, Rectangle bounds, int size, Color color, int order)
	{
		Object aa = graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		boolean centered = size >= ORB_NEXT;
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
		return new int[]{x + size / 2, y + size / 2, size / 2 + ring};
	}

	private static Color fade(Color color, float strength)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * strength));
	}

	/**
	 * Between two colours the shorter way round the colour wheel, so green to red goes by yellow and
	 * orange rather than through brown; a grey, having no hue, is met in a straight line instead.
	 */
	static Color blend(Color a, Color b, float t)
	{
		int alpha = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
		float[] from = Color.RGBtoHSB(a.getRed(), a.getGreen(), a.getBlue(), null);
		float[] to = Color.RGBtoHSB(b.getRed(), b.getGreen(), b.getBlue(), null);
		if (from[1] == 0 || to[1] == 0)
		{
			return new Color(
				Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
				Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t), alpha);
		}
		float turn = to[0] - from[0];
		turn -= Math.round(turn);
		Color c = Color.getHSBColor(from[0] + turn * t, from[1] + (to[1] - from[1]) * t, from[2] + (to[2] - from[2]) * t);
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
	}
}

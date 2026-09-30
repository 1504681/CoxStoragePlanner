package com.coxstorageplanner;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** The steps for the chest you have open, ticked off as the inventory changes. */
class ChestOverlay extends OverlayPanel
{
	private static final Color DONE = new Color(110, 200, 110);
	private static final Color NEXT = new Color(255, 220, 90);
	private static final Color TODO = Color.WHITE;

	private final CoxStoragePlannerPlugin plugin;

	@Inject
	ChestOverlay(CoxStoragePlannerPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		ChestProgress progress = plugin.getOpenChest();
		if (progress == null || (progress.deposits.isEmpty() && progress.withdrawals.isEmpty()))
		{
			return null;
		}
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(progress.plan.getName().isEmpty() ? "Chest" : progress.plan.getName())
			.color(progress.isDone() ? DONE : TODO)
			.build());
		ChestProgress.Step next = progress.next();
		int width = 0;
		for (ChestProgress.Step step : progress.deposits)
		{
			width = Math.max(width, line(graphics, "Put in", step, false));
		}
		for (ChestProgress.Step step : progress.withdrawals)
		{
			width = Math.max(width, line(graphics, progress.plan.isOrdered() ? step.order + "." : "Take", step, step == next));
		}
		panelComponent.setPreferredSize(new Dimension(width + 20, 0));
		return super.render(graphics);
	}

	private int line(Graphics2D graphics, String left, ChestProgress.Step step, boolean next)
	{
		String right = (step.done ? "✓ " : "") + step.line.text;
		Color color = step.done ? DONE : next ? NEXT : TODO;
		panelComponent.getChildren().add(LineComponent.builder()
			.left(left)
			.leftColor(color)
			.right(right)
			.rightColor(color)
			.build());
		return graphics.getFontMetrics().stringWidth(left + "  " + right);
	}
}

package com.coxstorageplanner;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Draws the sidebar off screen with a busy raid in it. The picture lands in build/panel.png. */
public class CoxStoragePanelTest
{
	private static final CoxStoragePanel.Actions NO_ACTIONS = new CoxStoragePanel.Actions()
	{
		@Override
		public void setNeed(Potion potion, int doses)
		{
		}

		@Override
		public void setNeedsTab(boolean solo)
		{
		}

		@Override
		public void setChestOrdered(String key, boolean ordered)
		{
		}

		@Override
		public void setChestLines(String key, boolean deposit, String text)
		{
		}

		@Override
		public void setMarking(boolean on)
		{
		}

		@Override
		public void selectChest(String key)
		{
		}

		@Override
		public void deleteChest(String key)
		{
		}

		@Override
		public void copyLoadout(String key)
		{
		}
	};

	private static PanelState busyRaid()
	{
		PanelState state = new PanelState();
		state.inParty = true;
		state.inventory = Supplies.of(new int[]{4, 8, 4, 0});
		state.privateStorage = Supplies.of(new int[]{0, 4, 0, 4});
		state.solo = true;
		state.separateSoloChests = true;
		state.suppliesTracker = true;
		state.inRaid = true;
		state.lineIcons.put("xeric's aid", 20984);
		state.lineIcons.put("stinkhorn mushroom", 20892);
		state.lineIcons.put("noxifer", 20901);
		state.units = NeedUnits.POTIONS;
		Needs needs = Needs.soloDefaults();
		for (Potion potion : Potion.values())
		{
			state.need.put(potion, potion.isSupply() ? needs.get(potion) : 0);
		}
		ChestPlan ice = state.chests.getOrCreate("RAIDS_ICE_DEMON#1", "Ice Demon");
		ice.getDeposit().add("Elder maul");
		ChestPlan farm = state.chests.getOrCreate("RAIDS_FARMING#1", "Farming 1");
		farm.getDeposit().add("everything else");
		farm.getWithdraw().addAll(Arrays.asList("wear Scythe of vitur", "Xeric's aid, 2", "Stinkhorn mushroom, 3", "Noxifer", "Overload", "Prayer enhance"));
		farm.setOrdered(true);
		state.currentChest = "RAIDS_FARMING#1";
		state.marking = true;
		state.carriedItems = ChestProgress.tally(Arrays.asList("Xeric's aid(4)", "Xeric's aid(3)", "Stinkhorn mushroom"));

		PanelState.Member me = new PanelState.Member();
		me.name = "brizzy";
		me.self = true;
		me.status = new MemberSupplies(Supplies.of(new int[]{4, 12, 4, 4}), null, null);
		PanelState.Member bob = new PanelState.Member();
		bob.name = "Zezima the 2nd";
		bob.status = new MemberSupplies(Supplies.of(new int[]{4, 8, 4, 4}),
			Supplies.of(new int[]{4, 0, 4, 4}), Supplies.of(new int[]{4, 16, 0, 0}));
		PanelState.Member quiet = new PanelState.Member();
		quiet.name = "No Plugin";
		state.team.addAll(Arrays.asList(me, bob, quiet));
		return state;
	}

	private static void layOut(Component component)
	{
		component.doLayout();
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				layOut(child);
			}
		}
	}

	private static int deepestRightEdge(Component component, int offset)
	{
		int edge = component.isVisible() ? offset + component.getX() + component.getWidth() : 0;
		if (component instanceof Container && component.isVisible())
		{
			for (Component child : ((Container) component).getComponents())
			{
				edge = Math.max(edge, deepestRightEdge(child, offset + component.getX()));
			}
		}
		return edge;
	}

	@Test
	public void drawsABusyRaidWithinTheSidebarWidth() throws Exception
	{
		draw("build/panel.png");
	}

	private static void draw(String file) throws Exception
	{
		BufferedImage[] drawn = new BufferedImage[1];
		int[] rightEdge = new int[1];
		SwingUtilities.invokeAndWait(() ->
		{
			CoxStoragePanel panel = new CoxStoragePanel(NO_ACTIONS, (label, itemId, quantity) ->
			{
				BufferedImage square = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = square.createGraphics();
				g.setColor(new Color(itemId * 9973 % 0xffffff));
				g.fillRoundRect(10, 4, 16, 24, 6, 6);
				g.dispose();
				label.setIcon(new ImageIcon(square));
			});
			panel.update(busyRaid());

			// twice, html labels only know their height once they have a width
			for (int pass = 0; pass < 2; pass++)
			{
				Dimension size = panel.getPreferredSize();
				panel.setSize(PluginPanel.PANEL_WIDTH, size.height);
				layOut(panel);
			}

			BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g = image.createGraphics();
			panel.paint(g);
			g.dispose();
			drawn[0] = image;
			rightEdge[0] = deepestRightEdge(panel, -panel.getX());
		});

		File out = new File(file);
		out.getParentFile().mkdirs();
		ImageIO.write(drawn[0], "png", out);

		assertEquals(PluginPanel.PANEL_WIDTH, drawn[0].getWidth());
		assertTrue("taller than it should be: " + drawn[0].getHeight(), drawn[0].getHeight() < 2500);
		assertTrue("something sticks out to x=" + rightEdge[0], rightEdge[0] <= PluginPanel.PANEL_WIDTH);
	}
}

package com.coxstorageplanner;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

class CoxStoragePanel extends PluginPanel
{
	interface Actions
	{
		void setNeed(Potion potion, int doses);

		/** Switches the doses needed between the team and the solo numbers. */
		void setNeedsTab(boolean solo);

		void renameChest(String key, String name);

		void setChestOrdered(String key, boolean ordered);

		/** Replaces a chest's deposit (true) or withdraw (false) list with the lines of the text. */
		void setChestLines(String key, boolean deposit, String text);

		/** Whether clicking items in a storage or the inventory adds them to the chest's lists. */
		void setMarking(boolean on);

		/** The chest shown in the sidebar, which inventory items are marked for when no storage is open. */
		void selectChest(String key);

		void deleteChest(String key);

		/** Replaces a chest's Take out with what's worn and carried right now, in order, and orders it. */
		void copyLoadout(String key);
	}

	interface Icons
	{
		void set(JLabel label, int itemId);
	}

	private static final Color GOOD = new Color(110, 200, 110);
	private static final Color BAD = new Color(255, 96, 96);
	private static final Color WARN = new Color(255, 170, 60);
	private static final Color MUTED = new Color(160, 160, 160);
	private static final Color WEAR = new Color(200, 130, 255);

	private static final int MAX_NAME = 12;

	/** A column of rows that each take the full width. */
	private static final class Stack extends JPanel
	{
		private int rows;

		private Stack(Color background)
		{
			setLayout(new GridBagLayout());
			if (background == null)
			{
				setOpaque(false);
			}
			else
			{
				setBackground(background);
			}
		}

		private void addRow(Component component, int gapAbove)
		{
			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0;
			c.gridy = rows++;
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.anchor = GridBagConstraints.NORTHWEST;
			c.insets = new Insets(gapAbove, 0, 0, 0);
			add(component, c);
		}

		private void clear()
		{
			removeAll();
			rows = 0;
		}
	}

	private final Actions actions;
	private final Icons icons;

	private final Map<Potion, JLabel> haveLabels = new EnumMap<>(Potion.class);
	private final Map<Potion, JLabel> detailLabels = new EnumMap<>(Potion.class);
	private final Map<Potion, JLabel> moreLabels = new EnumMap<>(Potion.class);
	private final Map<Potion, JTextField> needFields = new EnumMap<>(Potion.class);
	private final Map<Potion, JComponent> supplyRows = new EnumMap<>(Potion.class);
	private final JLabel teamTab = small("Team", Color.WHITE);
	private final JLabel soloTab = small("Solo", MUTED);
	private NeedUnits units = NeedUnits.POTIONS;
	private final JPanel storageGrid = new JPanel(new GridBagLayout());
	private final Stack chestsBody = new Stack(ColorScheme.DARKER_GRAY_COLOR);
	private final JComboBox<String> chestChooser = new JComboBox<>();
	private final JTextField chestName = new JTextField();
	private final JCheckBox chestOrdered = new JCheckBox("Withdraw in this order");
	private final JTextArea chestDeposit = new JTextArea(2, 10);
	private final JTextArea chestWithdraw = new JTextArea(3, 10);
	private final Stack chestSteps = new Stack(null);
	private String selectedChest;
	private final JLabel chestHere = small("", MUTED);
	private final JCheckBox chestMark = new JCheckBox("Mark by clicking");
	private final JPanel chestEditor = new JPanel(new BorderLayout());
	private final List<String> chestKeys = new ArrayList<>();
	/** The room chest the dropdown last jumped to by itself, so a pick of your own sticks. */
	private String jumpedTo;

	private boolean updating;
	private PanelState lastState;

	CoxStoragePanel(Actions actions, Icons icons)
	{
		this.actions = actions;
		this.icons = icons;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		Stack content = new Stack(null);
		content.addRow(header("Chests", chip("Delete", ColorScheme.DARKER_GRAY_COLOR,
			"Forget the chest shown below", () ->
			{
				if (selectedChest != null)
				{
					actions.deleteChest(selectedChest);
				}
			})), 0);
		content.addRow(buildChests(), 4);
		content.addRow(header("Supplies", needsTabs()), 12);
		content.addRow(buildSupplies(), 4);
		add(content, BorderLayout.NORTH);

		update(new PanelState());
	}

	private static JPanel header(String title, Component right)
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel label = new JLabel(title);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(label, BorderLayout.WEST);
		if (right != null)
		{
			header.add(right, BorderLayout.EAST);
		}
		return header;
	}

	private static JLabel small(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	private static JLabel chip(String text, Color background, String tooltip, Runnable onClick)
	{
		JLabel chip = new JLabel(text, SwingConstants.CENTER);
		chip.setFont(FontManager.getRunescapeSmallFont());
		chip.setForeground(Color.WHITE);
		chip.setOpaque(true);
		chip.setBackground(background);
		chip.setBorder(new EmptyBorder(3, 6, 3, 6));
		chip.setToolTipText(tooltip);
		if (onClick != null)
		{
			chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			chip.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (e.getButton() == MouseEvent.BUTTON1)
					{
						onClick.run();
					}
				}
			});
		}
		return chip;
	}

	private JPanel buildSupplies()
	{
		Stack body = new Stack(ColorScheme.DARKER_GRAY_COLOR);
		body.setBorder(new EmptyBorder(6, 6, 6, 6));
		boolean first = true;
		for (Potion potion : Potion.values())
		{
			if (!potion.isSupply())
			{
				continue;
			}

			JLabel icon = new JLabel();
			icon.setPreferredSize(new Dimension(36, 32));
			icons.set(icon, potion.getIconItemId());
			icon.setToolTipText(potion.getDisplayName());

			JLabel have = new JLabel();
			have.setFont(FontManager.getRunescapeSmallFont());
			JLabel detail = small("", MUTED);
			JLabel more = small("", MUTED);
			haveLabels.put(potion, have);
			detailLabels.put(potion, detail);
			moreLabels.put(potion, more);

			JPanel text = new JPanel(new GridLayout(4, 1));
			text.setOpaque(false);
			JLabel name = small(potion.getDisplayName(), Color.WHITE);
			text.add(name);
			text.add(have);
			text.add(detail);
			text.add(more);

			JTextField need = new JTextField("0");
			need.setFont(FontManager.getRunescapeSmallFont());
			need.setHorizontalAlignment(SwingConstants.CENTER);
			need.setMargin(new Insets(1, 1, 1, 1));
			need.setToolTipText(potion.getDisplayName() + " you want to have when you get to Olm");
			Runnable commit = () -> actions.setNeed(potion, units.parse(need.getText()));
			need.addActionListener(e -> commit.run());
			need.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					commit.run();
				}
			});
			needFields.put(potion, need);
			JPanel needHolder = new JPanel(new BorderLayout());
			needHolder.setOpaque(false);
			needHolder.setPreferredSize(new Dimension(36, 36));
			needHolder.add(small("need", MUTED), BorderLayout.NORTH);
			needHolder.add(need, BorderLayout.CENTER);

			JPanel row = new JPanel(new BorderLayout(6, 0));
			row.setOpaque(false);
			row.add(icon, BorderLayout.WEST);
			row.add(text, BorderLayout.CENTER);
			row.add(needHolder, BorderLayout.EAST);
			supplyRows.put(potion, row);
			body.addRow(row, first ? 0 : 6);
			first = false;
		}
		storageGrid.setOpaque(false);
		storageGrid.setToolTipText("Inventory + private storage of everyone in the party, the shared storage, and all of it added up");
		body.addRow(storageGrid, 8);
		return body;
	}

	/** Who holds what: a row per party member, one for the shared storage and a total. */
	private void rebuildStorageGrid(PanelState state)
	{
		storageGrid.removeAll();
		List<Potion> potions = new ArrayList<>();
		for (Potion potion : Potion.values())
		{
			if (potion.isSupply() && !potion.isSoloOnly())
			{
				potions.add(potion);
			}
		}
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(1, 0, 1, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridy = 0;
		gridRow(c, "", MUTED, potions, potion -> potion.getShortName(), null);

		int[] total = new int[Potion.values().length];
		List<PanelState.Member> members = state.team;
		if (members.isEmpty())
		{
			PanelState.Member me = new PanelState.Member();
			me.name = "You";
			me.self = true;
			me.status = new MemberSupplies(state.inventory, state.privateStorage, null);
			members = Collections.singletonList(me);
		}
		for (PanelState.Member member : members)
		{
			c.gridy++;
			Supplies held = member.status == null ? null : member.status.getHeld();
			boolean storageUnknown = member.status != null && member.status.getStored() == null;
			String note = held == null ? "Nothing known: ask them to install CoX Storage Planner from the Plugin Hub"
				: storageUnknown ? "Inventory only, " + (member.self ? "your" : "their")
				+ " private storage hasn't been opened this raid" : null;
			gridRow(c, member.self ? "You" : member.name, held == null ? MUTED : member.self ? GOOD : Color.WHITE, potions,
				potion -> held == null ? "×" : units.format(held.doses(potion)) + (storageUnknown ? "?" : ""), note);
			if (held != null)
			{
				for (Potion potion : potions)
				{
					total[potion.ordinal()] += held.doses(potion);
				}
			}
		}
		c.gridy++;
		Supplies shared = state.shared();
		gridRow(c, "Shared", Color.WHITE, potions, potion -> shared == null ? "?" : units.format(shared.doses(potion)),
			shared == null ? "Nobody has opened the shared storage this raid" : null);
		if (shared != null)
		{
			for (Potion potion : potions)
			{
				total[potion.ordinal()] += shared.doses(potion);
			}
		}
		c.gridy++;
		gridRow(c, "Total", MUTED, potions, potion -> units.format(total[potion.ordinal()]), null);
		storageGrid.revalidate();
	}

	private void gridRow(GridBagConstraints c, String name, Color color, List<Potion> potions,
		java.util.function.Function<Potion, String> cell, String tooltip)
	{
		c.gridx = 0;
		c.weightx = 1;
		JLabel label = small(name, color);
		label.setToolTipText(tooltip == null && name.length() > MAX_NAME ? name : tooltip);
		// a long name gets the column's share and an ellipsis, not a wider grid
		label.setPreferredSize(new Dimension(60, label.getPreferredSize().height));
		storageGrid.add(label, c);
		c.weightx = 0;
		for (Potion potion : potions)
		{
			c.gridx++;
			JLabel value = small(cell.apply(potion), color);
			value.setHorizontalAlignment(SwingConstants.RIGHT);
			value.setPreferredSize(new Dimension(30, value.getPreferredSize().height));
			value.setToolTipText(tooltip == null ? potion.getDisplayName() : tooltip);
			storageGrid.add(value, c);
		}
	}

	/** A chooser for the chest, its name, and the two lists with what the inventory says about them. */
	private JPanel buildChests()
	{
		chestsBody.setBorder(new EmptyBorder(6, 6, 6, 6));
		chestsBody.addRow(chestHere, 0);
		chestMark.setOpaque(false);
		chestMark.setFont(FontManager.getRunescapeSmallFont());
		chestMark.setForeground(Color.WHITE);
		chestMark.setToolTipText("<html>While on, left-clicking an item in a storage adds it to Take out and one in your inventory to Put in."
			+ "<br>Each click adds one more; Unmark on the right-click menu takes one away. Off again when you leave the raid.</html>");
		chestMark.addActionListener(e ->
		{
			if (!updating)
			{
				actions.setMarking(chestMark.isSelected());
			}
		});
		chestsBody.addRow(chestMark, 2);

		chestChooser.setFont(FontManager.getRunescapeSmallFont());
		chestChooser.setPreferredSize(new Dimension(100, 24));
		chestChooser.addActionListener(e ->
		{
			int index = chestChooser.getSelectedIndex();
			if (!updating && index >= 0 && index < chestKeys.size())
			{
				selectedChest = chestKeys.get(index);
				actions.selectChest(selectedChest);
				showChest(lastState);
			}
		});

		chestName.setFont(FontManager.getRunescapeSmallFont());
		chestName.setToolTipText("Your name for this chest");
		commitOn(chestName, () -> actions.renameChest(selectedChest, chestName.getText()));

		chestOrdered.setOpaque(false);
		chestOrdered.setFont(FontManager.getRunescapeSmallFont());
		chestOrdered.setForeground(Color.WHITE);
		chestOrdered.setToolTipText("Take things out top to bottom; the next one is lit up in the storage");
		chestOrdered.addActionListener(e ->
		{
			if (!updating && selectedChest != null)
			{
				actions.setChestOrdered(selectedChest, chestOrdered.isSelected());
			}
		});

		Stack editor = new Stack(null);
		editor.addRow(chestChooser, 0);
		editor.addRow(chestName, 4);
		editor.addRow(chestOrdered, 4);
		editor.addRow(small("Put in", Color.WHITE), 6);
		editor.addRow(listArea(chestDeposit, true), 2);
		JPanel takeOut = new JPanel(new BorderLayout());
		takeOut.setOpaque(false);
		takeOut.add(small("Take out", Color.WHITE), BorderLayout.WEST);
		takeOut.add(chip("Copy my loadout", ColorScheme.DARKER_GRAY_COLOR,
			"<html>Replace Take out with what you're wearing and carrying right now, in order,<br>"
				+ "and tick Withdraw in this order. Withdrawing it that way rebuilds the same inventory.</html>", () ->
			{
				if (selectedChest != null)
				{
					actions.copyLoadout(selectedChest);
				}
			}), BorderLayout.EAST);
		editor.addRow(takeOut, 6);
		editor.addRow(listArea(chestWithdraw, false), 2);
		editor.addRow(chestSteps, 6);
		chestEditor.setOpaque(false);
		chestEditor.add(editor, BorderLayout.CENTER);
		chestsBody.addRow(chestEditor, 4);
		return chestsBody;
	}

	private JTextArea listArea(JTextArea area, boolean deposit)
	{
		area.setFont(FontManager.getRunescapeSmallFont());
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setMargin(new Insets(3, 3, 3, 3));
		area.setToolTipText("<html>One item per line, matched from the start of its name, so 'Xeric's aid' is any dose."
			+ "<br>* and ? are wildcards: '*chinchompa', 'Dragon *'. 'Stinkhorn mushroom, 3' for a number"
			+ (deposit ? ", 'everything' to empty the inventory, 'everything else' to put away what Take out doesn't keep"
			: ", 'wear Scythe of vitur' for gear to put on first") + ".</html>");
		// saved as you type, so the lists count even if the game canvas never takes the focus back
		area.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				commit();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				commit();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				commit();
			}

			private void commit()
			{
				if (!updating && selectedChest != null)
				{
					actions.setChestLines(selectedChest, deposit, area.getText());
				}
			}
		});
		return area;
	}

	private static void commitOn(JTextField field, Runnable commit)
	{
		field.addActionListener(e -> commit.run());
		field.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent e)
			{
				commit.run();
			}
		});
	}

	private void updateChests(PanelState state)
	{
		List<ChestPlan> plans = CoxStoragePlannerPlugin.inRaidOrder(state.chests.all());
		// jump to the room's chest once it exists (it's made when its storage is first opened), once per room
		if (state.currentChest == null)
		{
			jumpedTo = null;
		}
		else if (!state.currentChest.equals(jumpedTo) && state.chests.get(state.currentChest) != null)
		{
			selectedChest = state.currentChest;
			jumpedTo = state.currentChest;
		}
		if (state.chests.get(selectedChest) == null)
		{
			selectedChest = state.chests.get(state.currentChest) != null ? state.currentChest
				: plans.isEmpty() ? null : plans.get(0).getKey();
		}

		chestKeys.clear();
		chestChooser.removeAllItems();
		for (ChestPlan plan : plans)
		{
			chestKeys.add(plan.getKey());
			chestChooser.addItem(plan.getName().isEmpty() ? plan.getKey() : plan.getName());
		}
		chestEditor.setVisible(!plans.isEmpty());
		chestMark.setSelected(state.marking);
		actions.selectChest(selectedChest);
		if (plans.isEmpty())
		{
			chestHere.setText("<html>Open a storage unit in a raid and it shows up here, with a list of what to put in and take out.</html>");
		}
		else if (state.currentChest == null)
		{
			chestHere.setText("Not at a chest");
		}
		else
		{
			ChestPlan here = state.chests.get(state.currentChest);
			chestHere.setText("At: " + (here == null ? CoxStoragePlannerPlugin.chestName(state.currentChest)
				: here.getName()));
		}
		showChest(state);
	}

	private void showChest(PanelState state)
	{
		ChestPlan plan = state == null ? null : state.chests.get(selectedChest);
		if (plan == null)
		{
			return;
		}
		int index = chestKeys.indexOf(plan.getKey());
		if (chestChooser.getSelectedIndex() != index)
		{
			chestChooser.setSelectedIndex(index);
		}
		setIfIdle(chestName, plan.getName());
		chestOrdered.setSelected(plan.isOrdered());
		setIfIdle(chestDeposit, String.join("\n", plan.getDeposit()));
		setIfIdle(chestWithdraw, String.join("\n", plan.getWithdraw()));

		chestSteps.clear();
		if (plan.getKey().equals(state.currentChest))
		{
			ChestProgress progress = state.openChest != null && state.openChest.plan.getKey().equals(plan.getKey())
				? state.openChest
				: new ChestProgress(plan, state.carriedItems, state.wornItems, state.carriedItems, null, state.putBack);
			ChestProgress.Step next = progress.next();
			for (ChestProgress.Step step : progress.wears)
			{
				chestSteps.addRow(small((step.done ? "✓ " : "• ") + "wear: " + step.line.name, step.done ? GOOD : WEAR), 0);
			}
			for (ChestProgress.Step step : progress.deposits)
			{
				chestSteps.addRow(small((step.done ? "✓ " : "• ") + "in: " + step.line.text, step.done ? GOOD : Color.WHITE), 0);
			}
			for (String name : progress.outOfOrder)
			{
				chestSteps.addRow(small("• back in: " + name + " (comes later)", WARN), 0);
			}
			for (ChestProgress.Step step : progress.withdrawals)
			{
				String prefix = step.missing ? "– " : step.done ? "✓ " : step == next ? "→ " : "• ";
				chestSteps.addRow(small(prefix + (plan.isOrdered() ? step.order + ". " : "out: ") + step.line.text
					+ (step.missing ? " (not here)" : ""),
					step.missing ? MUTED : step.done ? GOOD : step == next ? WARN : Color.WHITE), 0);
			}
		}
	}

	private static void setIfIdle(javax.swing.text.JTextComponent field, String text)
	{
		if (!field.hasFocus() && !field.getText().equals(text))
		{
			field.setText(text);
		}
	}

	/** Team | Solo, which set of "need" numbers is shown and edited. */
	private JPanel needsTabs()
	{
		JPanel tabs = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		tabs.setOpaque(false);
		for (JLabel tab : new JLabel[]{teamTab, soloTab})
		{
			tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			tab.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					actions.setNeedsTab(tab == soloTab);
				}
			});
			tabs.add(tab);
		}
		return tabs;
	}

	/** Must run on the Swing thread. */
	void update(PanelState state)
	{
		updating = true;
		try
		{
			lastState = state;
			updateChests(state);
			updateSupplies(state);
		}
		finally
		{
			updating = false;
		}
		revalidate();
		repaint();
	}

	private void updateSupplies(PanelState state)
	{
		units = state.units;
		for (Potion potion : haveLabels.keySet())
		{
			supplyRows.get(potion).setVisible(state.applies(potion));
			int need = state.need.getOrDefault(potion, 0);
			int have = state.have(potion);
			int shortfall = state.shortfall(potion);

			JLabel label = haveLabels.get(potion);
			if (need == 0)
			{
				label.setText(units.format(have) + " " + units.getWord());
				label.setForeground(Color.WHITE);
			}
			else if (shortfall == 0)
			{
				label.setText(units.format(have) + " / " + units.format(need) + " " + units.getWord());
				label.setForeground(GOOD);
			}
			else
			{
				label.setText(units.format(have) + " / " + units.format(need) + ", " + units.format(shortfall) + " more");
				label.setForeground(BAD);
			}

			String inventory = "inv " + units.format(state.inventory.doses(potion));
			String stored = "private " + (state.privateStorage == null ? "?" : units.format(state.privateStorage.doses(potion)));
			Supplies sharedStorage = state.shared();
			String shared = "shared " + (sharedStorage == null ? "?" : units.format(sharedStorage.doses(potion)));
			String split = potion != Potion.OVERLOAD ? "" : "<br>split overload " + units.format(state.splitHeld()) + " held"
				+ (state.countSplit ? "" : " (not counted)");
			JLabel detail = detailLabels.get(potion);
			detail.setText(inventory + "  " + stored);
			// the shared line only earns its row when it counts
			moreLabels.get(potion).setText(shared);
			moreLabels.get(potion).setVisible(state.countShared);
			detail.setToolTipText("<html>" + inventory + "<br>" + stored + "<br>" + shared + split
				+ (state.countShared ? "" : "<br>shared storage isn't counted (setting)")
				+ "<br><br>? means the storage hasn't been opened this raid</html>");
			label.setToolTipText(detail.getToolTipText());
			moreLabels.get(potion).setToolTipText(detail.getToolTipText());

			JTextField field = needFields.get(potion);
			String value = units.format(need);
			if (!field.hasFocus() && !field.getText().equals(value))
			{
				field.setText(value);
			}
		}
		teamTab.setForeground(state.solo ? MUTED : Color.WHITE);
		soloTab.setForeground(state.solo ? Color.WHITE : MUTED);
		String same = state.separateSoloNeeds ? "" : "<br>Same numbers for both until 'Separate doses for solo raids' is on in the settings";
		teamTab.setToolTipText("<html>What you need for Olm in a team raid" + same + "</html>");
		soloTab.setToolTipText("<html>What you need for Olm in a solo raid" + same + "</html>");
		rebuildStorageGrid(state);
	}
}

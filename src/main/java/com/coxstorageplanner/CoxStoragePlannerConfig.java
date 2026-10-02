package com.coxstorageplanner;

import java.awt.Color;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(CoxStoragePlannerConfig.GROUP)
public interface CoxStoragePlannerConfig extends Config
{
	String GROUP = "coxstorageplanner";
	String KEY_NEEDS = "needs";
	String KEY_NEEDS_SOLO = "needsSolo";
	String KEY_NEEDS_TAB_SOLO = "needsTabSolo";
	String KEY_CHESTS = "chests";
	String KEY_CHESTS_SOLO = "chestsSolo";
	String KEY_SEPARATE_SOLO_CHESTS = "separateSoloChests";
	String KEY_ICONS = "lineIcons";

	@ConfigSection(
		name = "Chests",
		description = "Deposit and withdraw lists per storage unit, set up in the sidebar",
		position = 0
	)
	String chestSection = "chests";

	@ConfigSection(
		name = "Sidebar",
		description = "When the sidebar icon shows and what's in the panel",
		position = 1
	)
	String sidebarSection = "sidebar";

	@ConfigSection(
		name = "Supplies tracker",
		description = "For the supplies tracker in the sidebar (Sidebar > Supplies tracker): what counts towards the doses you want for Olm",
		position = 2,
		closedByDefault = true
	)
	String needSection = "need";

	@ConfigItem(
		keyName = "chestGlow",
		name = "Glow items",
		description = "Outline the items still to move in the storage and the inventory",
		section = chestSection,
		position = 0
	)
	default boolean chestGlow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chestOrderedGlow",
		name = "Ordered withdrawal glow",
		description = "With an ordered withdrawal: glow just the next click, the next few with the biggest orb on the next one (how many is the setting below), or all of them",
		section = chestSection,
		position = 1
	)
	default ChestGlow chestOrderedGlow()
	{
		return ChestGlow.NEXT_FOUR;
	}

	@Range(min = 2, max = 120)
	@ConfigItem(
		keyName = "chestGlowCount",
		name = "Clicks shown",
		description = "How many clicks of an ordered withdrawal glow at a time, with the glow set to the next few",
		section = chestSection,
		position = 2
	)
	default int chestGlowCount()
	{
		return 4;
	}

	@ConfigItem(
		keyName = "chestGlowWidth",
		name = "Outline thickness",
		description = "How thick the outline around a glowing item is, in pixels, from 1 to 4. A half is a fainter pixel: 1.5 is one pixel with a faint second around it",
		section = chestSection,
		position = 10
	)
	default double chestGlowWidth()
	{
		return 1.5;
	}

	@ConfigItem(
		keyName = "chestScrollHint",
		name = "Light the scroll arrow",
		description = "When the next item to click is scrolled out of view in the storage, light the scroll bar's up or down arrow",
		section = chestSection,
		position = 3
	)
	default boolean chestScrollHint()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chestMarkers",
		name = "? and tick over the storage unit",
		description = "Draw a ? over the storage unit in your room while its chest has things to do, and a green tick once it's all done",
		section = chestSection,
		position = 4
	)
	default boolean chestMarkers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chestPutBack",
		name = "Organise the inventory",
		description = "With an ordered withdrawal, end up with the inventory in the order of the list: what you carry in the wrong slot is deposited again to come out in its place, or, when the storage is too full for that, gets an arrow to drag it to its slot. Off: no arrows, and the list only counts what you carry",
		section = chestSection,
		position = 5
	)
	default boolean chestPutBack()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SEPARATE_SOLO_CHESTS,
		name = "Separate chests for solo raids",
		description = "Keep a second set of chest plans for solo raids, picked by the raid's party size (the Team | Solo switch outside a raid). Starts as a copy of the team plans",
		section = chestSection,
		position = 6
	)
	default boolean separateSoloChests()
	{
		return false;
	}

	@Alpha
	@ConfigItem(
		keyName = "chestGlowColor",
		name = "Glow colour",
		description = "Outline colour, and the colour of an ordered withdrawal's first click",
		section = chestSection,
		position = 7
	)
	default Color chestGlowColor()
	{
		return new Color(255, 215, 0, 230);
	}

	@Alpha
	@ConfigItem(
		keyName = "chestGlowLastColor",
		name = "End colour",
		description = "Colour of the last click lit of an ordered withdrawal. The clicks lit shade from the glow colour on the next one to this one, so the colour shows how soon a click comes",
		section = chestSection,
		position = 8
	)
	default Color chestGlowLastColor()
	{
		return new Color(40, 230, 70, 230);
	}

	@Alpha
	@ConfigItem(
		keyName = "chestWearColor",
		name = "Wear colour",
		description = "Outline of gear still to put on, in the storage and the inventory; drawn a pixel heavier than the rest",
		section = chestSection,
		position = 9
	)
	default Color chestWearColor()
	{
		return new Color(195, 60, 255, 255);
	}

	@ConfigItem(
		keyName = "chestGlowPulse",
		name = "Pulse",
		description = "Make the outline breathe",
		section = chestSection,
		position = 11
	)
	default boolean chestGlowPulse()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideOutsideRaid",
		name = "Hide away from the Chambers",
		description = "Only show the sidebar icon in the Chambers of Xeric and outside its entrance on Mount Quidamortem",
		section = sidebarSection,
		position = 0
	)
	default boolean hideOutsideRaid()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideSidebar",
		name = "Always hide the sidebar icon",
		description = "Never show the sidebar icon. The glow and the marks keep working from the plans you already made",
		section = sidebarSection,
		position = 1
	)
	default boolean hideSidebar()
	{
		return false;
	}

	@ConfigItem(
		keyName = "suppliesTracker",
		name = "Supplies tracker",
		description = "Show the Supplies part of the sidebar: the doses you and your party hold against what you want for Olm. Its options are in the Supplies tracker section below",
		section = sidebarSection,
		position = 2
	)
	default boolean suppliesTracker()
	{
		return false;
	}

	@ConfigItem(
		keyName = "needUnits",
		name = "Show supplies as",
		description = "Whether the supply rows are in doses or in potions (4 doses)",
		section = needSection,
		position = 0
	)
	default NeedUnits needUnits()
	{
		return NeedUnits.POTIONS;
	}

	@ConfigItem(
		keyName = "separateSoloNeeds",
		name = "Separate doses for solo raids",
		description = "Keep separate doses for solo raids. Off means solos use the team numbers",
		section = needSection,
		position = 1
	)
	default boolean separateSoloNeeds()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countShared",
		name = "Count shared storage",
		description = "Count what's in shared storage towards the doses you need",
		section = needSection,
		position = 2
	)
	default boolean countShared()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countSplit",
		name = "Count split overloads",
		description = "Count a set of elder, twisted and kodai towards the overload doses you need",
		section = needSection,
		position = 3
	)
	default boolean countSplit()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_CHESTS,
		name = "Chests",
		description = "Chest plans, edited in the sidebar",
		hidden = true
	)
	default String chests()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_CHESTS_SOLO,
		name = "Chests, solo",
		description = "Chest plans for solo raids, edited in the sidebar",
		hidden = true
	)
	default String chestsSolo()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_ICONS,
		name = "List icons",
		description = "Item ids the plugin has seen for the lines of the chest lists, for the icons in the sidebar",
		hidden = true
	)
	default String lineIcons()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_NEEDS,
		name = "Doses needed",
		description = "Doses needed for Olm, edited in the sidebar",
		hidden = true
	)
	default String needs()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_NEEDS_SOLO,
		name = "Doses needed, solo",
		description = "Doses needed for Olm in a solo raid, edited in the sidebar",
		hidden = true
	)
	default String needsSolo()
	{
		return "";
	}

	@ConfigItem(
		keyName = KEY_NEEDS_TAB_SOLO,
		name = "Solo tab",
		description = "Whether the sidebar shows the solo doses outside a raid",
		hidden = true
	)
	default boolean needsTabSolo()
	{
		return false;
	}
}

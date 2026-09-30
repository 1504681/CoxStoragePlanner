package com.coxstorageplanner;

import java.awt.Color;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(CoxStoragePlannerConfig.GROUP)
public interface CoxStoragePlannerConfig extends Config
{
	String GROUP = "coxstorageplanner";
	String KEY_NEEDS = "needs";
	String KEY_NEEDS_SOLO = "needsSolo";
	String KEY_NEEDS_TAB_SOLO = "needsTabSolo";
	String KEY_CHESTS = "chests";

	@ConfigSection(
		name = "Supplies",
		description = "What counts towards the doses you need for Olm. The doses themselves are set in the sidebar.",
		position = 0
	)
	String needSection = "need";

	@ConfigSection(
		name = "Chests",
		description = "Deposit and withdraw lists per storage unit, set up in the sidebar",
		position = 1
	)
	String chestSection = "chests";

	@ConfigItem(
		keyName = "needUnits",
		name = "Show supplies as",
		description = "Whether the supply rows are in doses or in potions (4 doses)",
		section = needSection,
		position = 0
	)
	default NeedUnits needUnits()
	{
		return NeedUnits.DOSES;
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
		keyName = "trackStamina",
		name = "Stamina in solo raids",
		description = "Show a Stamina row and count it as short for Olm in solo raids",
		section = needSection,
		position = 2
	)
	default boolean trackStamina()
	{
		return false;
	}

	@ConfigItem(
		keyName = "countShared",
		name = "Count shared storage",
		description = "Count what's in shared storage towards the doses you need",
		section = needSection,
		position = 3
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
		position = 4
	)
	default boolean countSplit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "olmReminder",
		name = "Olm entry reminder",
		description = "Chat message with what you're still short when you enter Olm",
		section = needSection,
		position = 5
	)
	default boolean olmReminder()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chestOverlay",
		name = "Steps overlay",
		description = "List the chest's steps on screen while its storage is open",
		section = chestSection,
		position = 0
	)
	default boolean chestOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "chestGlow",
		name = "Glow items",
		description = "Outline the items still to move in the storage and the inventory",
		section = chestSection,
		position = 1
	)
	default boolean chestGlow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "chestOrderedGlow",
		name = "Ordered withdraw glow",
		description = "With an ordered withdraw list: glow just the next item, the next three with the biggest orb on the next one, or all of them from the first colour to the last",
		section = chestSection,
		position = 2
	)
	default ChestGlow chestOrderedGlow()
	{
		return ChestGlow.NEXT_THREE;
	}

	@Alpha
	@ConfigItem(
		keyName = "chestGlowColor",
		name = "Glow colour",
		description = "Outline colour, and the first colour of the gradient",
		section = chestSection,
		position = 3
	)
	default Color chestGlowColor()
	{
		return new Color(0, 255, 220, 220);
	}

	@Alpha
	@ConfigItem(
		keyName = "chestGlowLastColor",
		name = "Gradient end colour",
		description = "Colour of the last item in an ordered list when all of them glow",
		section = chestSection,
		position = 4
	)
	default Color chestGlowLastColor()
	{
		return new Color(255, 80, 200, 220);
	}

	@ConfigItem(
		keyName = "chestGlowPulse",
		name = "Pulse",
		description = "Make the outline breathe",
		section = chestSection,
		position = 5
	)
	default boolean chestGlowPulse()
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

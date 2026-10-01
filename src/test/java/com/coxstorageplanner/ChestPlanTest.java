package com.coxstorageplanner;

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ChestPlanTest
{
	@Test
	public void linesMatchFromTheStartWithACount()
	{
		ChestPlan.Line aid = new ChestPlan.Line("Xeric's aid");
		assertTrue(aid.matches("Xeric's aid(4)"));
		assertTrue(aid.matches("xeric's aid(1)"));
		assertFalse(aid.matches("Overload(4)"));
		ChestPlan.Line shrooms = new ChestPlan.Line("Stinkhorn mushroom, 3");
		ChestPlan.Line old = new ChestPlan.Line("Stinkhorn mushroom x3");
		assertEquals(3, old.count);
		assertEquals("Stinkhorn mushroom", old.name);
		assertEquals("Wilson, sr.", new ChestPlan.Line("Wilson, sr.").name);
		assertEquals("Stinkhorn mushroom", shrooms.name);
		assertEquals(3, shrooms.count);
		ChestPlan.Line axe = new ChestPlan.Line("Dragon axe");
		assertEquals(1, axe.count);
		assertTrue(new ChestPlan.Line("everything").everything);
		assertTrue(new ChestPlan.Line("Everything").matches("anything at all"));
	}

	@Test
	public void wildcardsMatchTheWholeName()
	{
		ChestPlan.Line chins = new ChestPlan.Line("*chinchompa");
		assertTrue(chins.matches("Black chinchompa"));
		assertTrue(chins.matches("Chinchompa"));
		assertFalse(chins.matches("Chinchompa gloves"));
		ChestPlan.Line dragon = new ChestPlan.Line("Dragon *, 2");
		assertTrue(dragon.matches("Dragon axe"));
		assertFalse(dragon.matches("Dragonstone"));
		assertEquals(2, dragon.count);
		assertTrue(new ChestPlan.Line("Xeric's aid(?)").matches("Xeric's aid(4)"));
		assertFalse(new ChestPlan.Line("Xeric's aid(?)").matches("Xeric's aid(+)(4)"));
	}

	@Test
	public void aCountedDepositIsDoneOnceThatManyWentIn()
	{
		ChestPlan plan = new ChestPlan("RAIDS_FARMING#1", "Farming 1");
		plan.getDeposit().add("Endarkened*, 2");
		Map<String, Integer> three = Collections.singletonMap("Endarkened juice", 3);
		Map<String, Integer> one = Collections.singletonMap("Endarkened juice", 1);
		Map<String, Integer> none = Collections.emptyMap();
		assertFalse(new ChestProgress(plan, three, three, none).deposits.get(0).done);
		assertTrue(new ChestProgress(plan, one, three, none).deposits.get(0).done);
		assertTrue(new ChestProgress(plan, three, three, Collections.singletonMap("Endarkened juice", 2)).deposits.get(0).done);
		assertFalse(new ChestProgress(plan, three, three, one).deposits.get(0).done);
		// without a number every one of them goes in
		plan.getDeposit().set(0, "Endarkened*");
		assertFalse(new ChestProgress(plan, one, three, three).deposits.get(0).done);
		assertTrue(new ChestProgress(plan, none, three, none).deposits.get(0).done);
	}

	@Test
	public void markingCountsClicksPerItem()
	{
		List<String> lines = new ArrayList<>(Collections.singletonList("*chinchompa"));
		assertTrue(ChestPlan.mark(lines, "Xeric's aid(4)", 1));
		assertTrue(ChestPlan.mark(lines, "Xeric's aid(3)", 1));
		assertTrue(ChestPlan.mark(lines, "Black chinchompa", 1));
		assertEquals(Arrays.asList("*chinchompa", "Xeric's aid, 2", "Black chinchompa"), lines);
		assertTrue(ChestPlan.mark(lines, "Xeric's aid(2)", -1));
		assertTrue(ChestPlan.mark(lines, "Black chinchompa", -1));
		assertEquals(Arrays.asList("*chinchompa", "Xeric's aid"), lines);
		assertFalse(ChestPlan.mark(lines, "Elder maul", -1));
		assertEquals(Arrays.asList("*chinchompa", "Xeric's aid"), lines);
	}

	@Test
	public void typedTextBecomesTrimmedLines()
	{
		assertEquals(Arrays.asList("Elder maul", "Overload"), ChestPlan.lines("  Elder maul \n\n Overload\n"));
		assertTrue(ChestPlan.lines(null).isEmpty());
	}

	@Test
	public void progressTicksOffWhatIsInTheInventory()
	{
		ChestPlan plan = new ChestPlan("RAIDS_FARMING#1", "Farming 1");
		plan.getDeposit().add("Elder maul");
		plan.getWithdraw().addAll(Arrays.asList("Xeric's aid, 2", "Stinkhorn mushroom", "Overload"));
		plan.setOrdered(true);

		ChestProgress before = new ChestProgress(plan, ChestProgress.tally(Arrays.asList("Elder maul", "Xeric's aid(4)")));
		assertFalse(before.deposits.get(0).done);
		assertFalse(before.withdrawals.get(0).done);
		assertEquals("Xeric's aid, 2", before.next().line.text);
		assertTrue(before.highlightsDeposit("Elder maul"));
		assertFalse(before.highlightsDeposit("Overload(4)"));
		// the maul has to go in before the take-outs light
		assertNull(before.highlightsWithdraw("Xeric's aid(3)", 1));
		assertFalse(before.isDone());

		ChestProgress maulIn = new ChestProgress(plan, ChestProgress.tally(Arrays.asList("Xeric's aid(4)")));
		assertEquals(1, maulIn.highlightsWithdraw("Xeric's aid(3)", 1).order);
		assertNull(maulIn.highlightsWithdraw("Overload(4)", 1));
		assertNull(maulIn.highlightsWithdraw("Overload(4)", 2));
		assertEquals(3, maulIn.highlightsWithdraw("Overload(4)", 3).order);
		assertEquals(2, maulIn.highlightsWithdraw("Overload(4)", 3).rank);

		ChestProgress after = new ChestProgress(plan,
			ChestProgress.tally(Arrays.asList("Xeric's aid(4)", "Xeric's aid(4)", "Stinkhorn mushroom", "Overload(4)")));
		assertTrue(after.isDone());
		assertNull(after.next());
		assertNull(after.highlightsWithdraw("Overload(4)", Integer.MAX_VALUE));
	}

	@Test
	public void depositEverythingMeansAnEmptyInventory()
	{
		ChestPlan plan = new ChestPlan("RAIDS_ICE_DEMON#1", "Ice demon");
		plan.getDeposit().add("everything");
		assertFalse(new ChestProgress(plan, Collections.singletonMap("Bronze dagger", 1)).isDone());
		assertTrue(new ChestProgress(plan, Collections.emptyMap()).isDone());
		assertTrue(new ChestProgress(plan, Collections.singletonMap("Bronze dagger", 1)).highlightsDeposit("Bronze dagger"));
	}

	@Test
	public void unorderedPlansHaveNoNext()
	{
		ChestPlan plan = new ChestPlan("k", "n");
		plan.getWithdraw().add("Overload");
		assertNull(new ChestProgress(plan, Collections.emptyMap()).next());
	}

	/** An instance template chunk the way the client packs it. */
	private static int chunk(int templateX, int templateY, int plane)
	{
		return plane << 24 | (templateX / 8) << 14 | (templateY / 8) << 3;
	}

	@Test
	public void roomsComeFromTheTemplateChunk()
	{
		assertEquals("RAIDS_END", CoxStoragePlannerPlugin.roomType(chunk(3264, 5152, 0)));
		// the End room that leads down to Olm sits past the width the API gives End
		assertEquals("RAIDS_END", CoxStoragePlannerPlugin.roomType(chunk(3264 + 72, 5152 + 16, 0)));
		assertEquals("RAIDS_FARMING", CoxStoragePlannerPlugin.roomType(chunk(3264, 5440, 0)));
		assertEquals("RAIDS_FARMING", CoxStoragePlannerPlugin.roomType(chunk(3264 + 40, 5440 + 8, 1)));
		assertEquals("RAIDS_TIGHTROPE", CoxStoragePlannerPlugin.roomType(chunk(3264, 5344, 1)));
		assertEquals("RAIDS_VESPULA", CoxStoragePlannerPlugin.roomType(chunk(3264, 5280, 2)));
		assertNull(CoxStoragePlannerPlugin.roomType(chunk(3264, 5184, 0)));
		assertNull(CoxStoragePlannerPlugin.roomType(chunk(3264 + 96, 5152, 0)));
		assertNull(CoxStoragePlannerPlugin.roomType(-1));
		assertEquals("Pre-Olm", CoxStoragePlannerPlugin.chestName("RAIDS_END#2"));
		assertEquals("Pre-Vanguards", CoxStoragePlannerPlugin.chestName("RAIDS_END#1"));
		ChestBook book = new ChestBook();
		for (String key : new String[]{"RAIDS_END#2", "RAIDS_FARMING#2", "RAIDS_SCAVENGERS#1", "RAIDS_ICE_DEMON#1", "RAIDS_END#1"})
		{
			book.getOrCreate(key, CoxStoragePlannerPlugin.chestName(key));
		}
		List<String> ordered = new ArrayList<>();
		for (ChestPlan plan : CoxStoragePlannerPlugin.inRaidOrder(book.all()))
		{
			ordered.add(plan.getName());
		}
		assertEquals(Arrays.asList("Ice Demon", "Pre-Vanguards", "Farming 2", "Pre-Olm", "Scavengers 1"), ordered);
		assertEquals("Farming 1", CoxStoragePlannerPlugin.chestName("RAIDS_FARMING#1"));
		assertEquals("Ice Demon", CoxStoragePlannerPlugin.chestName("RAIDS_ICE_DEMON#1"));
		assertEquals(1, CoxStoragePlannerPlugin.floor(3));
		assertEquals(2, CoxStoragePlannerPlugin.floor(2));
	}

	@Test
	public void wornGearCountsAsWithdrawnAndRepeatsAddUp()
	{
		ChestPlan plan = new ChestPlan("RAIDS_END#2", "End 2");
		plan.getWithdraw().addAll(Arrays.asList("Scythe of vitur", "Xeric's aid", "Overload", "Xeric's aid"));
		plan.setOrdered(true);
		Map<String, Integer> carried = ChestProgress.tally(Arrays.asList("Xeric's aid(4)", "Overload (+)(4)"));
		Map<String, Integer> worn = ChestProgress.tally(Arrays.asList("Scythe of vitur"));
		Map<String, Integer> none = Collections.emptyMap();
		ChestProgress progress = new ChestProgress(plan, carried, worn, carried, null);
		assertTrue(progress.withdrawals.get(0).done);
		assertTrue(progress.withdrawals.get(1).done);
		assertTrue(progress.withdrawals.get(2).done);
		assertFalse(progress.withdrawals.get(3).done);
		assertEquals(4, progress.next().order);
		assertFalse(new ChestProgress(plan, carried, none, carried, null).withdrawals.get(0).done);
	}

	@Test
	public void stepsWithNothingToTakeAreSkipped()
	{
		ChestPlan plan = new ChestPlan("RAIDS_END#2", "End 2");
		plan.getWithdraw().addAll(Arrays.asList("Ayak", "Xeric's aid, 2", "Overload"));
		plan.setOrdered(true);
		Map<String, Integer> carried = Collections.emptyMap();
		Map<String, Integer> storage = ChestProgress.tally(Arrays.asList("Xeric's aid(4)", "Overload (+)(4)"));
		ChestProgress progress = new ChestProgress(plan, carried, carried, carried, storage);
		assertTrue(progress.withdrawals.get(0).missing);
		assertTrue(progress.withdrawals.get(0).done);
		assertFalse(progress.withdrawals.get(1).missing);
		assertEquals(2, progress.next().order);
		// only one aid there: taking it makes the step done for lack of any more
		Map<String, Integer> oneOut = ChestProgress.tally(Arrays.asList("Xeric's aid(4)"));
		ChestProgress after = new ChestProgress(plan, oneOut, carried, carried, ChestProgress.tally(Arrays.asList("Overload (+)(4)")));
		assertTrue(after.withdrawals.get(1).missing);
		assertEquals(3, after.next().order);
		// a storage the client hasn't seen skips nothing
		ChestProgress unseen = new ChestProgress(plan, carried, carried, carried, null);
		assertFalse(unseen.withdrawals.get(0).missing);
		assertEquals(1, unseen.next().order);
	}

	@Test
	public void stepsSayHowManyAreStillToMove()
	{
		ChestPlan plan = new ChestPlan("RAIDS_ICE_DEMON#1", "Ice Demon");
		plan.getDeposit().addAll(Arrays.asList("Endarkened*, 11", "Bronze dagger"));
		plan.getWithdraw().addAll(Arrays.asList("wear Scythe of vitur", "Xeric's aid, 3", "Xeric's aid"));
		Map<String, Integer> carried = ChestProgress.tally(Arrays.asList("Endarkened juice", "Endarkened juice", "Endarkened juice",
			"Bronze dagger", "Bronze dagger", "Xeric's aid(4)"));
		Map<String, Integer> none = Collections.emptyMap();
		Map<String, Integer> storage = ChestProgress.tally(Arrays.asList("Endarkened juice", "Endarkened juice", "Endarkened juice",
			"Endarkened juice", "Scythe of vitur", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)"));
		ChestProgress progress = new ChestProgress(plan, carried, none, carried, storage);
		assertEquals(1, progress.wears.get(0).remaining);
		assertEquals(7, progress.deposits.get(0).remaining);
		assertEquals(Integer.MAX_VALUE, progress.deposits.get(1).remaining);
		assertEquals(2, progress.withdrawals.get(0).remaining);
		// the same item further down only counts its own
		assertEquals(1, progress.withdrawals.get(1).remaining);
		assertEquals(0, new ChestProgress(plan, carried, ChestProgress.tally(Arrays.asList("Scythe of vitur")), carried, storage)
			.wears.get(0).remaining);
	}

	@Test
	public void everyClickHasItsNumber()
	{
		ChestPlan plan = new ChestPlan("RAIDS_END#2", "Pre-Olm");
		plan.getWithdraw().addAll(Arrays.asList("Ayak", "Overload", "Stinkhorn mushroom, 3", "Xeric's aid, 2", "Xeric's aid", "Noxifer"));
		plan.setOrdered(true);
		Map<String, Integer> none = Collections.emptyMap();
		Map<String, Integer> storage = new java.util.LinkedHashMap<>(ChestProgress.tally(Arrays.asList("Overload (+)(4)",
			"Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Noxifer")));
		storage.put("Stinkhorn mushroom", 8);
		java.util.Set<String> stacks = Collections.singleton("Stinkhorn mushroom");
		ChestProgress progress = new ChestProgress(plan, none, none, none, storage, false, stacks);
		// the ayak isn't anywhere, so it takes no number and the overload is click 1
		assertTrue(progress.withdrawals.get(0).missing);
		assertEquals(1, progress.withdrawals.get(1).click);
		assertEquals(0, progress.withdrawals.get(1).rank);
		// a stack is one click whatever the count
		assertTrue(progress.withdrawals.get(2).stack);
		assertEquals(2, progress.withdrawals.get(2).click);
		// two aids are clicks 3 and 4, the third aid further down is 5
		assertEquals(3, progress.withdrawals.get(3).click);
		assertEquals(2, progress.withdrawals.get(3).rank);
		assertEquals(5, progress.withdrawals.get(4).click);
		assertEquals(6, progress.withdrawals.get(5).click);
		assertEquals(6, progress.clicks);
		assertEquals(Arrays.asList(progress.withdrawals.get(3), progress.withdrawals.get(4)),
			progress.withdrawSteps("Xeric's aid(4)", Integer.MAX_VALUE));
		// only steps within the next four clicks: the aid on the second line is the fifth
		assertEquals(Arrays.asList(progress.withdrawals.get(3)), progress.withdrawSteps("Xeric's aid(4)", 4));
		assertTrue(progress.wantsNext("Overload (+)(4)"));
		assertFalse(progress.wantsNext("Xeric's aid(4)"));

		// overload, mushrooms and one aid out: the numbers stay put, the aid left to take is still click 4
		Map<String, Integer> carried = new java.util.LinkedHashMap<>(ChestProgress.tally(Arrays.asList("Overload (+)(4)", "Xeric's aid(4)")));
		carried.put("Stinkhorn mushroom", 3);
		storage = new java.util.LinkedHashMap<>(ChestProgress.tally(Arrays.asList(
			"Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Noxifer")));
		storage.put("Stinkhorn mushroom", 5);
		progress = new ChestProgress(plan, carried, none, none, storage, false, stacks);
		assertTrue(progress.withdrawals.get(2).done);
		assertEquals(4, progress.withdrawals.get(3).click);
		assertEquals(0, progress.withdrawals.get(3).rank);
		assertEquals(1, progress.withdrawals.get(3).remaining);
		assertEquals(5, progress.withdrawals.get(4).click);
		assertEquals(1, progress.withdrawals.get(4).rank);
		assertEquals(6, progress.clicks);
	}

	@Test
	public void twoOfFiveLightAsTwoClicks()
	{
		ChestPlan plan = new ChestPlan("RAIDS_END#2", "Pre-Olm");
		plan.getWithdraw().addAll(Arrays.asList("Overload", "Xeric's aid, 2", "Noxifer", "Xeric's aid, 2"));
		plan.setOrdered(true);
		Map<String, Integer> none = Collections.emptyMap();
		Map<String, Integer> storage = ChestProgress.tally(Arrays.asList("Overload (+)(4)", "Noxifer",
			"Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)"));
		ChestProgress progress = new ChestProgress(plan, none, none, none, storage);

		// five aids in the storage, one frame: 2 and 3, then (past the noxifer) 5 and 6, the fifth dark
		ChestItemOverlay overlay = new ChestItemOverlay(null, null, null, null);
		int[] numbers = new int[5];
		for (int i = 0; i < 5; i++)
		{
			ChestItemOverlay.Click click = overlay.withdrawal(progress, "Xeric's aid(4)", 1, Integer.MAX_VALUE);
			numbers[i] = click == null ? 0 : click.number;
		}
		assertEquals("[2, 3, 5, 6, 0]", Arrays.toString(numbers));
		assertEquals(1, overlay.withdrawal(progress, "Overload (+)(4)", 1, Integer.MAX_VALUE).number);
		assertEquals(4, overlay.withdrawal(progress, "Noxifer", 1, Integer.MAX_VALUE).number);

		// with the next four clicks lit, the fifth and sixth wait
		overlay = new ChestItemOverlay(null, null, null, null);
		for (int i = 0; i < 5; i++)
		{
			ChestItemOverlay.Click click = overlay.withdrawal(progress, "Xeric's aid(4)", 1, 4);
			numbers[i] = click == null ? 0 : click.number;
		}
		assertEquals("[2, 3, 0, 0, 0]", Arrays.toString(numbers));
		ChestItemOverlay.Click last = overlay.withdrawal(progress, "Noxifer", 1, 4);
		assertEquals(4, last.number);
		assertEquals(3, last.rank);

		// one aid standing for all five (the last of them is the one to click): clicks 2 and 3, then the noxifer is in between
		overlay = new ChestItemOverlay(null, null, null, null);
		ChestItemOverlay.Click run = overlay.withdrawal(progress, "Xeric's aid(4)", 5, Integer.MAX_VALUE);
		assertEquals(2, run.number);
		assertEquals(2, run.count);
		assertEquals(1, run.rank);
	}

	@Test
	public void fullestPotionLightsFirst()
	{
		ChestPlan plan = new ChestPlan("RAIDS_END#2", "End 2");
		plan.getWithdraw().addAll(Arrays.asList("Xeric's aid, 2"));
		plan.setOrdered(true);
		Map<String, Integer> none = Collections.emptyMap();
		Map<String, Integer> storage = ChestProgress.tally(Arrays.asList("Xeric's aid(2)", "Xeric's aid(4)", "Xeric's aid(3)"));
		ChestProgress progress = new ChestProgress(plan, none, none, none, storage);
		assertNotNull(progress.highlightsWithdraw("Xeric's aid(4)", 3));
		assertNull(progress.highlightsWithdraw("Xeric's aid(3)", 3));
		assertNull(progress.highlightsWithdraw("Xeric's aid(2)", 3));
		Map<String, Integer> no4s = ChestProgress.tally(Arrays.asList("Xeric's aid(2)", "Xeric's aid(3)"));
		ChestProgress later = new ChestProgress(plan, ChestProgress.tally(Arrays.asList("Xeric's aid(4)")), none, none, no4s);
		assertNotNull(later.highlightsWithdraw("Xeric's aid(3)", 3));
		assertNull(later.highlightsWithdraw("Xeric's aid(2)", 3));
		assertEquals(-1, ChestProgress.dose("Scythe of vitur"));
		assertEquals(4, ChestProgress.dose("Overload (+)(4)"));
	}

	@Test
	public void loadoutLinesFollowTheSlotsAndMergeRuns()
	{
		List<String> names = Arrays.asList("Infernal cape", null, "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(3)",
			"Endarkened juice", null, "Overload (+)(4)", "Xeric's aid(4)");
		List<Integer> quantities = Arrays.asList(1, 0, 1, 1, 1, 11, 0, 1, 1);
		assertEquals(Arrays.asList("Infernal cape", "Xeric's aid, 3", "Endarkened juice, 11", "Overload (+)", "Xeric's aid"),
			ChestProgress.loadoutLines(names, quantities, ""));
		assertEquals(Arrays.asList("wear Infernal cape"),
			ChestProgress.loadoutLines(Arrays.asList("Infernal cape"), Arrays.asList(1), "wear "));
		assertTrue(ChestProgress.loadoutLines(Arrays.asList((String) null), Arrays.asList(0), "").isEmpty());
	}

	@Test
	public void wearThenPutInThenTakeOut()
	{
		ChestPlan plan = new ChestPlan("RAIDS_END#2", "End 2");
		plan.getDeposit().add("everything else");
		plan.getWithdraw().addAll(Arrays.asList("wear Scythe of vitur", "wear Infernal cape", "Xeric's aid, 2", "Overload"));
		plan.setOrdered(true);
		Map<String, Integer> none = Collections.emptyMap();

		// nothing worn yet: only the gear lights, wherever it is
		Map<String, Integer> carried = ChestProgress.tally(Arrays.asList("Infernal cape", "Bronze dagger", "Xeric's aid(4)"));
		ChestProgress progress = new ChestProgress(plan, carried, none, carried, null);
		assertEquals(ChestProgress.Phase.WEAR, progress.phase());
		assertTrue(progress.highlightsWear("Scythe of vitur"));
		assertTrue(progress.highlightsWear("Infernal cape"));
		assertFalse(progress.highlightsDeposit("Bronze dagger"));
		assertNull(progress.highlightsWithdraw("Overload (+)(4)", 3));
		assertEquals(2, progress.wears.size());
		assertEquals(2, progress.withdrawals.size());
		assertEquals("Xeric's aid, 2", progress.withdrawals.get(0).line.text);

		// worn: the dagger has to go, the aid stays
		Map<String, Integer> worn = ChestProgress.tally(Arrays.asList("Scythe of vitur", "Infernal cape"));
		carried = ChestProgress.tally(Arrays.asList("Bronze dagger", "Xeric's aid(4)"));
		progress = new ChestProgress(plan, carried, worn, carried, null);
		assertEquals(ChestProgress.Phase.DEPOSIT, progress.phase());
		assertFalse(progress.highlightsWear("Scythe of vitur"));
		assertTrue(progress.highlightsDeposit("Bronze dagger"));
		assertFalse(progress.highlightsDeposit("Xeric's aid(4)"));
		assertNull(progress.highlightsWithdraw("Overload (+)(4)", 3));

		// dagger gone: now the ordered take-out, and the aid already counts once
		carried = ChestProgress.tally(Arrays.asList("Xeric's aid(4)"));
		progress = new ChestProgress(plan, carried, worn, carried, null);
		assertEquals(ChestProgress.Phase.WITHDRAW, progress.phase());
		assertTrue(progress.deposits.get(0).done);
		assertEquals(1, progress.highlightsWithdraw("Xeric's aid(4)", 3).order);
		assertEquals(2, progress.highlightsWithdraw("Overload (+)(4)", 3).order);
		assertFalse(progress.isDone());

		carried = ChestProgress.tally(Arrays.asList("Xeric's aid(4)", "Xeric's aid(4)", "Overload (+)(4)"));
		assertTrue(new ChestProgress(plan, carried, worn, carried, null).isDone());
	}

	@Test
	public void eitherOfTwoNamesWillDo()
	{
		ChestPlan.Line line = ChestPlan.parse(Arrays.asList("Ayak | Sang* staff*, 1")).get(0);
		assertEquals(1, line.count);
		assertTrue(line.counted);
		assertTrue(line.matches("Sanguinesti staff"));
		assertTrue(line.matches("Ayak's blessing"));
		assertFalse(line.matches("Kodai wand"));
		ChestPlan.Line pipe = ChestPlan.parse(Arrays.asList("wear Torva full helm | Neitiznot faceguard")).get(0);
		assertTrue(pipe.wear);
		assertTrue(pipe.matches("Neitiznot faceguard"));
		assertTrue(pipe.matches("Torva full helm"));
		// "or" is just a word, not a choice
		assertFalse(ChestPlan.parse(Arrays.asList("Ayak or Sang* staff*")).get(0).matches("Sanguinesti staff"));
		// marking never edits an "A | B" line
		java.util.List<String> lines = new java.util.ArrayList<>(Arrays.asList("Ayak | Sang* staff*"));
		assertTrue(ChestPlan.mark(lines, "Sanguinesti staff", 1));
		assertEquals(Arrays.asList("Ayak | Sang* staff*", "Sanguinesti staff"), lines);
	}

	@Test
	public void wearLinesParse()
	{
		ChestPlan.Line line = ChestPlan.parse(Arrays.asList("wear Scythe of vitur")).get(0);
		assertTrue(line.wear);
		assertEquals("Scythe of vitur", line.name);
		assertTrue(line.matches("Scythe of vitur"));
		ChestPlan.Line counted = ChestPlan.parse(Arrays.asList("Equip Rada's blessing, 2")).get(0);
		assertTrue(counted.wear);
		assertEquals(2, counted.count);
		assertEquals("Rada's blessing", counted.name);
		assertTrue(ChestPlan.parse(Arrays.asList("everything else")).get(0).everythingElse);
		assertFalse(ChestPlan.parse(Arrays.asList("everything")).get(0).everythingElse);
	}

	@Test
	public void carriedItemsOfLaterStepsGoBackFirst()
	{
		ChestPlan plan = new ChestPlan("RAIDS_END#2", "Pre-Olm");
		plan.getWithdraw().addAll(Arrays.asList("Xeric's aid, 2", "Overload", "Prayer enhance"));
		plan.setOrdered(true);
		Map<String, Integer> none = Collections.emptyMap();

		// the enhance is carried before the aid and the overload: back in it goes
		Map<String, Integer> carried = ChestProgress.tally(Arrays.asList("Xeric's aid(4)", "Prayer enhance(4)"));
		ChestProgress progress = new ChestProgress(plan, carried, none, carried, null, true);
		assertEquals(Arrays.asList("Prayer enhance(4)"), progress.outOfOrder);
		assertEquals(ChestProgress.Phase.DEPOSIT, progress.phase());
		assertTrue(progress.highlightsDeposit("Prayer enhance(4)"));
		assertFalse(progress.highlightsDeposit("Xeric's aid(4)"));
		assertNull(progress.highlightsWithdraw("Xeric's aid(4)", 3));

		// with the setting off it's just the plan as written
		ChestProgress loose = new ChestProgress(plan, carried, none, carried, null, false);
		assertTrue(loose.outOfOrder.isEmpty());
		assertEquals(1, loose.highlightsWithdraw("Xeric's aid(4)", 3).order);

		// put back: the next step lights again
		carried = ChestProgress.tally(Arrays.asList("Xeric's aid(4)"));
		progress = new ChestProgress(plan, carried, none, carried, null, true);
		assertTrue(progress.outOfOrder.isEmpty());
		assertEquals(1, progress.highlightsWithdraw("Xeric's aid(4)", 3).order);

		// the item of the next step, or of a done one, is never out of order
		carried = ChestProgress.tally(Arrays.asList("Xeric's aid(4)", "Xeric's aid(4)", "Overload (+)(4)"));
		assertTrue(new ChestProgress(plan, carried, none, carried, null, true).outOfOrder.isEmpty());
	}

	@Test
	public void chestsRoundTripThroughJson()
	{
		ChestBook book = new ChestBook();
		ChestPlan plan = book.getOrCreate("RAIDS_END#2", "Pre-Olm");
		plan.getDeposit().add("everything else");
		plan.getWithdraw().addAll(Arrays.asList("wear Scythe of vitur", "Xeric's aid, 2"));
		plan.setOrdered(true);
		Gson gson = new Gson();
		ChestBook back = ChestBook.parse(book.encode(gson), gson);
		assertEquals(book.encode(gson), back.encode(gson));
		assertTrue(back.get("RAIDS_END#2").isOrdered());
		assertTrue(ChestBook.parse("", gson).all().isEmpty());
		assertTrue(ChestBook.parse("not json", gson).all().isEmpty());
	}
}

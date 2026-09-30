package com.coxstorageplanner;

import com.google.gson.Gson;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
		assertEquals(1, before.highlightsWithdraw("Xeric's aid(3)", 1).order);
		assertNull(before.highlightsWithdraw("Overload(4)", 1));
		assertNull(before.highlightsWithdraw("Overload(4)", 2));
		assertEquals(3, before.highlightsWithdraw("Overload(4)", 3).order);
		assertEquals(2, before.rank(before.highlightsWithdraw("Overload(4)", 3)));
		assertFalse(before.isDone());

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

	@Test
	public void defaultChestsParseAndRoundTrip()
	{
		ChestBook book = ChestBook.defaults();
		assertEquals(3, book.all().size());
		ChestPlan end = book.get("RAIDS_END#1");
		assertTrue(end.isOrdered());
		assertEquals(19, end.getWithdraw().size());
		ChestProgress progress = new ChestProgress(end, ChestProgress.tally(Arrays.asList("Revitalisation(4)")));
		assertEquals("Scythe of Vitur", progress.next().line.text);
		Gson gson = new Gson();
		assertEquals(book.encode(gson), ChestBook.parse(book.encode(gson), gson).encode(gson));
		assertTrue(ChestBook.isUnset(""));
		assertFalse(ChestBook.isUnset("[]"));
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
		assertEquals("End 2", CoxStoragePlannerPlugin.chestName("RAIDS_END#2"));
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
		ChestProgress progress = new ChestProgress(plan, carried, worn, carried, none);
		assertTrue(progress.withdrawals.get(0).done);
		assertTrue(progress.withdrawals.get(1).done);
		assertTrue(progress.withdrawals.get(2).done);
		assertFalse(progress.withdrawals.get(3).done);
		assertEquals(4, progress.next().order);
		assertFalse(new ChestProgress(plan, carried, none, carried, none).withdrawals.get(0).done);
	}

	@Test
	public void loadoutLinesFollowTheSlotsAndMergeRuns()
	{
		List<String> names = Arrays.asList("Infernal cape", null, "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(3)",
			"Endarkened juice", null, "Overload (+)(4)", "Xeric's aid(4)");
		List<Integer> quantities = Arrays.asList(1, 0, 1, 1, 1, 11, 0, 1, 1);
		assertEquals(Arrays.asList("Infernal cape", "Xeric's aid, 3", "Endarkened juice, 11", "Overload (+)", "Xeric's aid"),
			ChestProgress.loadoutLines(names, quantities));
		assertTrue(ChestProgress.loadoutLines(Arrays.asList((String) null), Arrays.asList(0)).isEmpty());
	}
}

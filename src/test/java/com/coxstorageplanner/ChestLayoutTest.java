package com.coxstorageplanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class ChestLayoutTest
{
	private static final int SLOTS = 28;

	/** An inventory and a storage to click through: a withdrawal lands in the first empty slot, a deposit leaves a hole. */
	private static final class Game
	{
		final ChestPlan plan;
		final String[] slots = new String[SLOTS];
		final int[] quantities = new int[SLOTS];
		final Map<String, Integer> storage = new LinkedHashMap<>();
		final Set<String> stackable;
		final int capacity;
		boolean withdrawing;
		int deposits;
		int withdrawals;
		/** How often the plan went from putting in to taking out. */
		int rounds;

		Game(ChestPlan plan, int capacity, Set<String> stackable, String... carried)
		{
			this.plan = plan;
			this.capacity = capacity;
			this.stackable = stackable;
			for (int i = 0; i < carried.length; i++)
			{
				slots[i] = carried[i];
				quantities[i] = carried[i] == null ? 0 : 1;
			}
		}

		Game store(String name, int quantity)
		{
			storage.merge(name, quantity, Integer::sum);
			return this;
		}

		Map<String, Integer> inventory()
		{
			Map<String, Integer> items = new LinkedHashMap<>();
			for (int i = 0; i < SLOTS; i++)
			{
				if (slots[i] != null)
				{
					items.merge(slots[i], quantities[i], Integer::sum);
				}
			}
			return items;
		}

		ChestProgress progress()
		{
			Map<String, Integer> inventory = inventory();
			ChestProgress progress = new ChestProgress(plan, inventory, Collections.emptyMap(), inventory, new HashMap<>(storage),
				true, stackable, new ChestProgress.Carried(slots.clone(), quantities.clone(), capacity < 0 ? -1 : capacity - storage.size(), withdrawing));
			withdrawing = progress.withdrawing;
			return progress;
		}

		void deposit(int slot)
		{
			assertTrue("storage has room for " + slots[slot], capacity < 0 || storage.containsKey(slots[slot]) || storage.size() < capacity);
			storage.merge(slots[slot], quantities[slot], Integer::sum);
			slots[slot] = null;
			quantities[slot] = 0;
			deposits++;
		}

		void withdraw(String name, int quantity)
		{
			int left = storage.get(name) - quantity;
			if (left > 0)
			{
				storage.put(name, left);
			}
			else
			{
				storage.remove(name);
			}
			withdrawals++;
			for (int i = 0; i < SLOTS; i++)
			{
				if (stackable.contains(name) && name.equals(slots[i]))
				{
					quantities[i] += quantity;
					return;
				}
			}
			for (int i = 0; i < SLOTS; i++)
			{
				if (slots[i] == null)
				{
					slots[i] = name;
					quantities[i] = quantity;
					return;
				}
			}
			throw new AssertionError("inventory full");
		}

		/** Does what lights up, one click at a time, until the plan is done or stuck. @return the last progress */
		ChestProgress play()
		{
			ChestProgress.Phase last = null;
			for (int click = 0; click < 400; click++)
			{
				ChestProgress progress = progress();
				if (progress.isDone() || progress.blocked)
				{
					return progress;
				}
				if (progress.phase() == ChestProgress.Phase.WITHDRAW && last == ChestProgress.Phase.DEPOSIT)
				{
					rounds++;
				}
				last = progress.phase();
				if (progress.phase() == ChestProgress.Phase.DEPOSIT)
				{
					int slot = -1;
					for (int i = 0; i < SLOTS && slot < 0; i++)
					{
						if (slots[i] != null && progress.depositsSlot(i, slots[i]))
						{
							slot = i;
						}
					}
					assertTrue("something to put in", slot >= 0);
					deposit(slot);
				}
				else
				{
					ChestProgress.Click next = progress.queue().get(0);
					String pick = null;
					for (String name : storage.keySet())
					{
						if (next.step.line.matches(name) && progress.fullest(next.step.line, name))
						{
							pick = name;
						}
					}
					assertTrue("the next click is in the storage", pick != null);
					assertTrue(progress.wantsNext(pick));
					withdraw(pick, next.step.stack ? next.step.remaining : 1);
				}
			}
			throw new AssertionError("never finished");
		}

		List<String> carried()
		{
			List<String> names = new ArrayList<>(Arrays.asList(slots));
			while (!names.isEmpty() && names.get(names.size() - 1) == null)
			{
				names.remove(names.size() - 1);
			}
			return names;
		}
	}

	private static ChestPlan plan(String putIn, String... takeOut)
	{
		ChestPlan plan = new ChestPlan("RAIDS_END#1", "Pre-Vanguards");
		if (putIn != null)
		{
			plan.getDeposit().add(putIn);
		}
		plan.getWithdraw().addAll(Arrays.asList(takeOut));
		plan.setOrdered(true);
		return plan;
	}

	@Test
	public void gearCarriedInTheWrongSlotGoesBackIn()
	{
		// the three were never put in, so they sit where the list's first items belong
		ChestPlan plan = plan("everything else", "Twisted bow", "Dragon claws", "Avernic defender", "Emberlight", "Elder maul", "Xeric's aid, 2");
		Game game = new Game(plan, 25, Collections.emptySet(), "Avernic defender", "Emberlight", "Elder maul", "Shark")
			.store("Twisted bow", 1).store("Dragon claws", 1).store("Xeric's aid(4)", 5);
		ChestProgress progress = game.progress();
		assertTrue(progress.exact());
		assertEquals(ChestProgress.Phase.DEPOSIT, progress.phase());
		assertEquals(Arrays.asList("Avernic defender", "Emberlight", "Elder maul"), progress.outOfOrder);
		assertTrue(progress.depositsSlot(0, "Avernic defender"));
		assertTrue(progress.depositsSlot(3, "Shark"));
		assertFalse(progress.isDone());

		progress = game.play();
		assertTrue(progress.isDone());
		assertEquals(Arrays.asList("Twisted bow", "Dragon claws", "Avernic defender", "Emberlight", "Elder maul",
			"Xeric's aid(4)", "Xeric's aid(4)"), game.carried());
		assertEquals(1, game.rounds);
	}

	@Test
	public void whatIsInItsSlotStays()
	{
		ChestPlan plan = plan("everything", "Twisted bow", "Dragon claws", "Elder maul");
		Game game = new Game(plan, 25, Collections.emptySet(), "Twisted bow", "Shark", "Elder maul").store("Dragon claws", 1);
		ChestProgress progress = game.progress();
		assertFalse(progress.depositsSlot(0, "Twisted bow"));
		assertTrue(progress.depositsSlot(1, "Shark"));
		assertFalse(progress.depositsSlot(2, "Elder maul"));
		assertTrue(progress.withdrawals.get(0).done);
		assertFalse(progress.withdrawals.get(1).done);
		assertTrue(progress.withdrawals.get(2).done);
		assertEquals(2, progress.withdrawals.get(1).click);
		assertTrue(game.play().isDone());
		assertEquals(1, game.deposits);
		assertEquals(1, game.withdrawals);
		assertEquals(Arrays.asList("Twisted bow", "Dragon claws", "Elder maul"), game.carried());
	}

	@Test
	public void aFullStorageSwapsInRounds()
	{
		// 25 slots, 23 taken: two things go in, two come out, and so on
		String[] wanted = new String[10];
		String[] junk = new String[10];
		ChestPlan plan = plan("everything else");
		Game game = new Game(plan, 25, Collections.emptySet());
		for (int i = 0; i < 10; i++)
		{
			wanted[i] = "Gear " + (char) ('a' + i);
			junk[i] = "Junk " + (char) ('a' + i);
			plan.getWithdraw().add(wanted[i]);
			game.slots[i] = junk[i];
			game.quantities[i] = 1;
			game.store(wanted[i], 1);
		}
		for (int i = 0; i < 13; i++)
		{
			game.store("Filler " + i, 1);
		}
		ChestProgress progress = game.progress();
		assertEquals(2, progress.free);
		assertTrue(progress.depositsSlot(0, junk[0]));
		assertTrue(progress.depositsSlot(1, junk[1]));
		assertFalse(progress.depositsSlot(2, junk[2]));

		progress = game.play();
		assertTrue(progress.isDone());
		assertEquals(Arrays.asList(wanted), game.carried());
		assertEquals(5, game.rounds);
		assertEquals(23, game.storage.size());
	}

	@Test
	public void aFullInventoryGoesThroughATinyStorage()
	{
		// 28 carried, three of them gear of the list in the wrong place, and 25 other things for a storage with 19 slots left
		ChestPlan plan = plan("everything else", "Twisted bow", "Dragon claws", "Avernic defender", "Emberlight", "Elder maul",
			"Xeric's aid, 6", "Overload", "Prayer enhance, 2", "Revitalisation, 3");
		Game game = new Game(plan, 25, Collections.emptySet(), "Avernic defender", "Emberlight", "Elder maul")
			.store("Twisted bow", 1).store("Dragon claws", 1).store("Xeric's aid(4)", 6).store("Overload (+)(4)", 1)
			.store("Prayer enhance(4)", 2).store("Revitalisation(4)", 3);
		for (int i = 3; i < SLOTS; i++)
		{
			game.slots[i] = "Junk " + i;
			game.quantities[i] = 1;
		}
		assertEquals(19, game.progress().free);
		ChestProgress progress = game.play();
		assertFalse(progress.blocked);
		assertTrue(progress.isDone());
		assertEquals(Arrays.asList("Twisted bow", "Dragon claws", "Avernic defender", "Emberlight", "Elder maul",
			"Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)",
			"Overload (+)(4)", "Prayer enhance(4)", "Prayer enhance(4)", "Revitalisation(4)", "Revitalisation(4)", "Revitalisation(4)"),
			game.carried());
		// 19 in, the 17 of the list out, then the 9 that were left
		assertEquals(28, game.deposits);
		assertEquals(17, game.withdrawals);
		assertEquals(25, game.storage.size());
	}

	@Test
	public void aRoundOfWithdrawalsIsFinishedBeforeMoreGoesIn()
	{
		ChestPlan plan = plan("everything else", "Gear a", "Gear b", "Gear c");
		Game game = new Game(plan, 4, Collections.emptySet(), "Junk a", "Junk b", "Junk c")
			.store("Gear a", 1).store("Gear b", 1).store("Gear c", 1);
		// one slot free: junk a in, and now the storage is full
		game.deposit(0);
		ChestProgress progress = game.progress();
		assertEquals(ChestProgress.Phase.WITHDRAW, progress.phase());
		assertEquals(1, progress.queue().size());
		assertEquals(1, progress.queue().get(0).number);
		assertNull(progress.highlightsWithdraw("Gear b", 4));
		game.withdraw("Gear a", 1);
		progress = game.progress();
		// nothing more can come out, so it's the next deposit's turn
		assertEquals(ChestProgress.Phase.DEPOSIT, progress.phase());
		assertTrue(progress.depositsSlot(1, "Junk b"));
		assertFalse(progress.depositsSlot(2, "Junk c"));
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Gear a", "Gear b", "Gear c"), game.carried());
	}

	@Test
	public void theDepositThatLetsSomethingOutGoesFirst()
	{
		// slot 0 wants the bow, which is carried in slot 2; slot 1 wants the claws, which are in the storage
		ChestPlan plan = plan("everything else", "Twisted bow", "Dragon claws", "Elder maul");
		Game game = new Game(plan, 2, Collections.emptySet(), "Shark", "Elder maul", "Twisted bow").store("Dragon claws", 1);
		ChestProgress progress = game.progress();
		assertEquals(1, progress.free);
		assertFalse(progress.depositsSlot(0, "Shark"));
		assertTrue(progress.depositsSlot(1, "Elder maul"));
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Twisted bow", "Dragon claws", "Elder maul"), game.carried());
	}

	@Test
	public void aFullStorageWithNothingToTakeOutIsBlocked()
	{
		ChestPlan plan = plan("everything else", "Twisted bow");
		Game game = new Game(plan, 2, Collections.emptySet(), "Shark").store("Twisted bow", 1).store("Filler", 1);
		ChestProgress progress = game.play();
		assertTrue(progress.blocked);
		assertFalse(progress.isDone());
		assertFalse(progress.depositsSlot(0, "Shark"));
	}

	@Test
	public void whatTheStorageAlreadyHoldsTakesNoSlot()
	{
		ChestPlan plan = plan("everything else", "Twisted bow");
		Game game = new Game(plan, 2, Collections.emptySet(), "Shark", "Shark").store("Twisted bow", 1).store("Shark", 4);
		ChestProgress progress = game.progress();
		assertEquals(0, progress.free);
		assertFalse(progress.blocked);
		assertTrue(progress.depositsSlot(0, "Shark"));
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Twisted bow"), game.carried());
	}

	@Test
	public void whatNeitherListTouchesKeepsItsSlot()
	{
		// no put-in list: the rune pouch stays where it is and the list fills in around it
		ChestPlan plan = plan(null, "Twisted bow", "Dragon claws");
		Game game = new Game(plan, 25, Collections.emptySet(), "Rune pouch", "Dragon claws").store("Twisted bow", 1);
		ChestProgress progress = game.progress();
		assertFalse(progress.depositsSlot(0, "Rune pouch"));
		assertTrue(progress.depositsSlot(1, "Dragon claws"));
		assertEquals(Arrays.asList("Dragon claws"), progress.outOfOrder);
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Rune pouch", "Twisted bow", "Dragon claws"), game.carried());
	}

	@Test
	public void aStackIsOneSlotAndOneClick()
	{
		ChestPlan plan = plan("everything else", "Twisted bow", "Dragon arrow, 100", "Elder maul");
		Game game = new Game(plan, 25, Collections.singleton("Dragon arrow"), "Elder maul")
			.store("Twisted bow", 1).store("Dragon arrow", 250);
		ChestProgress progress = game.progress();
		assertEquals(3, progress.clicks);
		progress = game.play();
		assertTrue(progress.isDone());
		assertEquals(Arrays.asList("Twisted bow", "Dragon arrow", "Elder maul"), game.carried());
		assertEquals(100, game.quantities[1]);
		assertEquals(150, (int) game.storage.get("Dragon arrow"));
	}

	@Test
	public void aShortStackIsToppedUpWhereItSits()
	{
		ChestPlan plan = plan(null, "Dragon arrow, 100", "Twisted bow");
		Game game = new Game(plan, 25, Collections.singleton("Dragon arrow"), "Dragon arrow", "Twisted bow").store("Dragon arrow", 250);
		game.quantities[0] = 40;
		ChestProgress progress = game.progress();
		assertFalse(progress.isDone());
		assertEquals(ChestProgress.Phase.WITHDRAW, progress.phase());
		assertEquals(60, progress.withdrawals.get(0).remaining);
		assertTrue(game.play().isDone());
		assertEquals(100, game.quantities[0]);
	}

	@Test
	public void whatIsNowhereLeavesNoGap()
	{
		ChestPlan plan = plan("everything else", "Twisted bow", "Dragon claws", "Elder maul");
		Game game = new Game(plan, 25, Collections.emptySet(), "Shark").store("Twisted bow", 1).store("Elder maul", 1);
		ChestProgress progress = game.progress();
		assertTrue(progress.withdrawals.get(1).missing);
		assertEquals(2, progress.clicks);
		assertEquals(2, progress.withdrawals.get(2).click);
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Twisted bow", "Elder maul"), game.carried());
	}

	@Test
	public void potionsComeOutFullestFirstAndInTheirSlots()
	{
		ChestPlan plan = plan("everything else", "Xeric's aid, 3", "Overload");
		Game game = new Game(plan, 25, Collections.emptySet(), "Overload (+)(4)", "Xeric's aid(2)")
			.store("Xeric's aid(4)", 1).store("Xeric's aid(3)", 1);
		ChestProgress progress = game.progress();
		// the overload is where an aid belongs; the aid in slot 1 is where it should be
		assertTrue(progress.depositsSlot(0, "Overload (+)(4)"));
		assertFalse(progress.depositsSlot(1, "Xeric's aid(2)"));
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Xeric's aid(4)", "Xeric's aid(2)", "Xeric's aid(3)", "Overload (+)(4)"), game.carried());
	}

	@Test
	public void countsStillRuleWithoutTheSlotsOrTheSetting()
	{
		ChestPlan plan = plan("everything else", "Twisted bow", "Elder maul");
		Map<String, Integer> carried = ChestProgress.tally(Arrays.asList("Elder maul"));
		Map<String, Integer> storage = Collections.singletonMap("Twisted bow", 1);
		ChestProgress.Carried slots = new ChestProgress.Carried(Arrays.asList("Elder maul"));
		assertTrue(new ChestProgress(plan, carried, Collections.emptyMap(), carried, storage, true, Collections.emptySet(), slots).exact());
		assertFalse(new ChestProgress(plan, carried, Collections.emptyMap(), carried, storage, false, Collections.emptySet(), slots).exact());
		assertFalse(new ChestProgress(plan, carried, Collections.emptyMap(), carried, storage, true, Collections.emptySet()).exact());
		plan.setOrdered(false);
		assertFalse(new ChestProgress(plan, carried, Collections.emptyMap(), carried, storage, true, Collections.emptySet(), slots).exact());
	}

	@Test
	public void aFullStorageLetsThingsOutFirstWhenGoingByCounts()
	{
		ChestPlan plan = plan("everything else", "Twisted bow", "Elder maul");
		plan.setOrdered(false);
		Map<String, Integer> carried = ChestProgress.tally(Arrays.asList("Shark", "Elder maul"));
		Map<String, Integer> storage = Collections.singletonMap("Twisted bow", 1);
		String[] names = new String[SLOTS];
		int[] quantities = new int[SLOTS];
		names[0] = "Shark";
		names[1] = "Elder maul";
		quantities[0] = quantities[1] = 1;
		ChestProgress full = new ChestProgress(plan, carried, Collections.emptyMap(), carried, storage, true, Collections.emptySet(),
			new ChestProgress.Carried(names, quantities, 0, false));
		assertEquals(ChestProgress.Phase.WITHDRAW, full.phase());
		assertEquals(1, full.highlightsWithdraw("Twisted bow", 1).order);
		assertFalse(full.blocked);
		ChestProgress roomy = new ChestProgress(plan, carried, Collections.emptyMap(), carried, storage, true, Collections.emptySet(),
			new ChestProgress.Carried(names, quantities, 3, false));
		assertEquals(ChestProgress.Phase.DEPOSIT, roomy.phase());
		assertTrue(roomy.highlightsDeposit("Shark"));
	}
}

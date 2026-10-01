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
		int drags;
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

		/** Storage slots taken: one per item, one per stack of a stackable one. */
		int used()
		{
			int used = 0;
			for (Map.Entry<String, Integer> e : storage.entrySet())
			{
				used += stackable.contains(e.getKey()) ? 1 : e.getValue();
			}
			return used;
		}

		ChestProgress progress()
		{
			Map<String, Integer> inventory = inventory();
			ChestProgress progress = new ChestProgress(plan, inventory, Collections.emptyMap(), inventory, new HashMap<>(storage),
				true, stackable, new ChestProgress.Carried(slots.clone(), quantities.clone(), capacity < 0 ? -1 : capacity - used(), withdrawing));
			withdrawing = progress.withdrawing;
			return progress;
		}

		void deposit(int slot)
		{
			assertTrue("storage has room for " + slots[slot],
				capacity < 0 || (stackable.contains(slots[slot]) && storage.containsKey(slots[slot])) || used() < capacity);
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
				if (progress.phase() == ChestProgress.Phase.MOVE)
				{
					last = progress.phase();
					int from = progress.moveFrom();
					int to = progress.moveTo();
					assertEquals(slots[from], progress.moveName());
					String name = slots[to];
					int quantity = quantities[to];
					slots[to] = slots[from];
					quantities[to] = quantities[from];
					slots[from] = name;
					quantities[from] = quantity;
					drags++;
					continue;
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
	public void theEndOfTheListStaysWhereItIsCarried()
	{
		// the pouch lives in the last slot and never goes in; the list was copied from a full inventory
		ChestPlan plan = plan("everything else", "Scythe of vitur", "Elder maul", "Xeric's aid, 2", "Overload", "Divine rune pouch");
		Game game = new Game(plan, 25, Collections.emptySet(), "Scythe of vitur", "Elder maul", "Shark")
			.store("Xeric's aid(4)", 2);
		game.slots[27] = "Divine rune pouch (l)";
		game.quantities[27] = 1;
		ChestProgress progress = game.progress();
		assertTrue(progress.outOfOrder.isEmpty());
		assertFalse(progress.depositsSlot(27, "Divine rune pouch (l)"));
		assertFalse(progress.depositsSlot(0, "Scythe of vitur"));
		assertTrue(progress.depositsSlot(2, "Shark"));
		assertTrue(progress.withdrawals.get(4).done);
		// the overload is nowhere, which doesn't move the pouch either
		assertTrue(progress.withdrawals.get(3).missing);
		assertTrue(game.play().isDone());
		assertEquals(1, game.deposits);
		assertEquals(Arrays.asList("Scythe of vitur", "Elder maul", "Xeric's aid(4)", "Xeric's aid(4)"), game.carried().subList(0, 4));
		assertNull(game.slots[4]);
		assertEquals("Divine rune pouch (l)", game.slots[27]);

		// carried above where the rest will land, it's in the way and goes back in
		game = new Game(plan, 25, Collections.emptySet(), "Divine rune pouch (l)", "Scythe of vitur").store("Elder maul", 1);
		progress = game.progress();
		assertEquals(Arrays.asList("Divine rune pouch (l)", "Scythe of vitur"), progress.outOfOrder);
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Scythe of vitur", "Elder maul", "Divine rune pouch (l)"), game.carried());
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
		assertEquals(23, game.used());
	}

	@Test
	public void aFullInventoryGoesThroughATinyStorage()
	{
		// 28 carried, three of them gear of the list in the wrong place, and 25 other things for a storage with 11 slots left
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
		ChestProgress progress = game.progress();
		assertEquals(11, progress.free);
		// too tight to put the gear back in: it's dragged to its slots instead
		assertEquals(ChestProgress.Phase.MOVE, progress.phase());
		assertEquals("Avernic defender", progress.moveName());
		assertEquals(2, progress.moveTo());
		progress = game.play();
		assertFalse(progress.blocked);
		assertTrue(progress.isDone());
		assertEquals(Arrays.asList("Twisted bow", "Dragon claws", "Avernic defender", "Emberlight", "Elder maul",
			"Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)",
			"Overload (+)(4)", "Prayer enhance(4)", "Prayer enhance(4)", "Revitalisation(4)", "Revitalisation(4)", "Revitalisation(4)"),
			game.carried());
		assertEquals(3, game.drags);
		assertEquals(25, game.deposits);
		assertEquals(14, game.withdrawals);
		assertEquals(25, game.used());
	}

	@Test
	public void everyItemTakesAStorageSlot()
	{
		// five sharks and one slot: one lights, though the storage holds sharks already
		ChestPlan plan = plan("everything else", "Twisted bow");
		Game game = new Game(plan, 4, Collections.emptySet(), null, "Shark", "Shark", "Shark", "Shark", "Shark")
			.store("Twisted bow", 1).store("Shark", 2);
		ChestProgress progress = game.progress();
		assertEquals(1, progress.free);
		assertEquals(ChestProgress.Phase.DEPOSIT, progress.phase());
		assertEquals(1, progress.depositsNamed("Shark"));
		int lit = 0;
		for (int slot = 0; slot < SLOTS; slot++)
		{
			lit += game.slots[slot] != null && progress.depositsSlot(slot, game.slots[slot]) ? 1 : 0;
		}
		assertEquals(1, lit);
		// and none once it's full: the bow comes out first
		game.deposit(1);
		progress = game.progress();
		assertEquals(0, progress.free);
		assertEquals(ChestProgress.Phase.WITHDRAW, progress.phase());
		assertEquals(0, progress.depositsNamed("Shark"));
		for (int slot = 0; slot < SLOTS; slot++)
		{
			assertFalse(game.slots[slot] != null && progress.depositsSlot(slot, game.slots[slot]));
		}
	}

	@Test
	public void aFullStorageComesOutFourAtATime()
	{
		// eight things in the way, the storage full, and the only empty slots below them
		ChestPlan plan = plan("everything else");
		String[] wanted = new String[8];
		Game game = new Game(plan, 8, Collections.emptySet());
		for (int i = 0; i < 8; i++)
		{
			wanted[i] = "Gear " + (char) ('a' + i);
			plan.getWithdraw().add(wanted[i]);
			game.slots[i] = "Junk " + (char) ('a' + i);
			game.quantities[i] = 1;
			game.store(wanted[i], 1);
		}
		ChestProgress progress = game.progress();
		assertEquals(0, progress.free);
		assertFalse(progress.blocked);
		assertEquals(ChestProgress.Phase.WITHDRAW, progress.phase());
		assertEquals(4, progress.queue().size());
		assertEquals(1, progress.queue().get(0).number);
		assertEquals(4, progress.queue().get(3).number);
		for (int i = 0; i < 4; i++)
		{
			game.progress();
			game.withdraw(wanted[i], 1);
		}
		// they landed in slots 9 to 12, and now go where they belong; what's there takes their place
		progress = game.progress();
		assertEquals(ChestProgress.Phase.MOVE, progress.phase());
		assertEquals(4, progress.moves().size());
		assertEquals(8, progress.moveFrom());
		assertEquals(0, progress.moveTo());
		assertEquals("Gear a", progress.moveName());
		assertTrue(progress.queue().isEmpty());
		assertEquals(0, progress.depositsNamed("Junk e"));

		progress = game.play();
		assertTrue(progress.isDone());
		assertEquals(Arrays.asList(wanted), game.carried());
		assertEquals(4, game.drags);
		assertEquals(8, game.deposits);
		assertEquals(8, game.withdrawals);
	}

	@Test
	public void aFullStorageAndAFullInventoryAreBlocked()
	{
		ChestPlan plan = plan("everything else", "Twisted bow");
		Game game = new Game(plan, 1, Collections.emptySet()).store("Twisted bow", 1);
		for (int i = 0; i < SLOTS; i++)
		{
			game.slots[i] = "Junk " + i;
			game.quantities[i] = 1;
		}
		ChestProgress progress = game.play();
		assertTrue(progress.blocked);
		assertEquals(0, game.deposits + game.withdrawals + game.drags);
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
	public void gearInTheWrongSlotIsDraggedWhenTheStorageIsTight()
	{
		// the bow is carried where the maul belongs and the maul where the claws do; one slot left in the storage
		ChestPlan plan = plan("everything else", "Twisted bow", "Dragon claws", "Elder maul");
		Game game = new Game(plan, 2, Collections.emptySet(), "Shark", "Elder maul", "Twisted bow").store("Dragon claws", 1);
		ChestProgress progress = game.progress();
		assertEquals(1, progress.free);
		assertEquals(ChestProgress.Phase.MOVE, progress.phase());
		assertEquals(2, progress.moveFrom());
		assertEquals(0, progress.moveTo());
		assertFalse(progress.depositsSlot(0, "Shark"));
		assertFalse(progress.depositsSlot(1, "Elder maul"));
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Twisted bow", "Dragon claws", "Elder maul"), game.carried());
		assertEquals(2, game.drags);
		assertEquals(1, game.deposits);
		assertEquals(1, game.withdrawals);
	}

	@Test
	public void aFullStorageLetsOneOutToMakeRoom()
	{
		// nothing fits and the bow's slot is taken: the bow comes out next to it, which makes room for the shark
		ChestPlan plan = plan("everything else", "Twisted bow");
		Game game = new Game(plan, 2, Collections.emptySet(), "Shark").store("Twisted bow", 1).store("Filler", 1);
		ChestProgress progress = game.progress();
		assertFalse(progress.blocked);
		assertFalse(progress.depositsSlot(0, "Shark"));
		assertEquals(ChestProgress.Phase.WITHDRAW, progress.phase());
		assertEquals(1, progress.queue().size());
		progress = game.play();
		assertTrue(progress.isDone());
		// the end of the list stays where it's carried, so no drag for the one item
		assertEquals(Arrays.asList(null, "Twisted bow"), game.carried());
		assertEquals(1, game.deposits);
		assertEquals(2, game.used());
	}

	@Test
	public void aFullStorageAsksForADragInstead()
	{
		// the book is seven slots down from where it belongs, with an aid in its place, and nothing fits in the storage
		ChestPlan plan = plan("everything else", "Scythe of vitur", "Book of the dead", "Xeric's aid, 3", "Overload");
		Game game = new Game(plan, 1, Collections.emptySet(), "Scythe of vitur", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)",
			"Book of the dead").store("Overload (+)(4)", 1);
		ChestProgress progress = game.progress();
		assertEquals(0, progress.free);
		assertFalse(progress.blocked);
		assertFalse(progress.isDone());
		assertEquals(4, progress.moveFrom());
		assertEquals(1, progress.moveTo());
		assertEquals("Book of the dead", progress.moveName());
		assertFalse(progress.depositsSlot(4, "Book of the dead"));

		assertTrue(game.play().isDone());
		assertEquals(1, game.drags);
		assertEquals(0, game.deposits);
		assertEquals(Arrays.asList("Scythe of vitur", "Book of the dead", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)",
			"Overload (+)(4)"), game.carried());

		// with room in the storage it's a redeposit as usual, and no drag
		game = new Game(plan, 5, Collections.emptySet(), "Scythe of vitur", "Xeric's aid(4)", "Xeric's aid(4)", "Xeric's aid(4)",
			"Book of the dead").store("Overload (+)(4)", 1);
		progress = game.progress();
		assertEquals(-1, progress.moveFrom());
		assertTrue(progress.depositsSlot(4, "Book of the dead"));
		assertTrue(game.play().isDone());
		assertEquals(0, game.drags);
	}

	@Test
	public void anyInventoryAndStorageComeToAnEnd()
	{
		java.util.Random random = new java.util.Random(7);
		int finished = 0;
		for (int run = 0; run < 3000; run++)
		{
			ChestPlan plan = plan(random.nextInt(5) == 0 ? null : "everything else");
			int gear = 1 + random.nextInt(12);
			List<String> units = new ArrayList<>();
			for (int i = 0; i < gear; i++)
			{
				plan.getWithdraw().add("Gear " + (char) ('a' + i));
				units.add("Gear " + (char) ('a' + i));
				if (i == gear / 2 && random.nextBoolean())
				{
					int aids = 1 + random.nextInt(4);
					plan.getWithdraw().add("Xeric's aid, " + aids);
					for (int a = 0; a < aids + random.nextInt(3); a++)
					{
						units.add(random.nextBoolean() ? "Xeric's aid(4)" : "Xeric's aid(3)");
					}
				}
			}
			for (int j = random.nextInt(16); j > 0; j--)
			{
				units.add("Junk " + j);
			}
			List<Integer> empty = new ArrayList<>();
			for (int i = 0; i < SLOTS; i++)
			{
				empty.add(i);
			}
			Collections.shuffle(empty, random);
			Map<String, Integer> stored = new LinkedHashMap<>();
			String[] carried = new String[SLOTS];
			for (String unit : units)
			{
				int where = random.nextInt(unit.startsWith("Junk") ? 2 : 5);
				if (where == 0 && !empty.isEmpty())
				{
					carried[empty.remove(0)] = unit;
				}
				else if (where < 4 || unit.startsWith("Junk"))
				{
					stored.merge(unit, 1, Integer::sum);
				}
			}
			int held = 0;
			for (int quantity : stored.values())
			{
				held += quantity;
			}
			Game game = new Game(plan, random.nextInt(3) == 0 ? 120 : held + random.nextInt(4), Collections.emptySet(), carried);
			game.storage.putAll(stored);
			String start = Arrays.toString(carried) + " " + stored + " of " + game.capacity + " " + plan.getWithdraw();
			ChestProgress progress;
			try
			{
				progress = game.play();
			}
			catch (AssertionError e)
			{
				throw new AssertionError("run " + run + ": " + start, e);
			}
			assertTrue(start, game.used() <= game.capacity);
			finished += progress.isDone() ? 1 : 0;
			if (game.capacity == 120)
			{
				// with room for everything nothing gets in the way, and nothing is dragged
				assertTrue(start, progress.isDone());
				assertEquals(start, 0, game.drags);
			}
		}
		assertTrue("most of them get done: " + finished, finished > 2400);
	}

	@Test
	public void stepsAreNumberedByClick()
	{
		ChestPlan plan = plan("everything else", "Xeric's aid, 2", "Lockpick", "Overload", "Prayer enhance");
		Game game = new Game(plan, 25, Collections.emptySet()).store("Xeric's aid(4)", 2).store("Overload (+)(4)", 1).store("Prayer enhance(4)", 1);
		ChestProgress progress = game.progress();
		assertEquals(1, progress.withdrawals.get(0).first);
		assertEquals(2, progress.withdrawals.get(0).span);
		// the lockpick is nowhere: no number, and the overload is click 3
		assertTrue(progress.withdrawals.get(1).missing);
		assertEquals(0, progress.withdrawals.get(1).span);
		assertEquals(3, progress.withdrawals.get(2).first);
		assertEquals(1, progress.withdrawals.get(2).span);
		assertEquals(4, progress.withdrawals.get(3).first);
		// the same going by counts
		plan.setOrdered(true);
		ChestProgress counts = new ChestProgress(plan, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
			new HashMap<>(game.storage), false, Collections.emptySet());
		assertEquals(0, counts.withdrawals.get(1).span);
		assertEquals(3, counts.withdrawals.get(2).first);
		assertEquals(2, counts.withdrawals.get(0).span);
	}

	@Test
	public void aStackJoinsTheOneInTheStorage()
	{
		ChestPlan plan = plan("everything else", "Twisted bow");
		Game game = new Game(plan, 2, Collections.singleton("Dragon arrow"), "Dragon arrow").store("Twisted bow", 1).store("Dragon arrow", 40);
		game.quantities[0] = 60;
		ChestProgress progress = game.progress();
		assertEquals(0, progress.free);
		assertFalse(progress.blocked);
		assertTrue(progress.depositsSlot(0, "Dragon arrow"));
		assertTrue(game.play().isDone());
		assertEquals(Arrays.asList("Twisted bow"), game.carried());
		assertEquals(100, (int) game.storage.get("Dragon arrow"));
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

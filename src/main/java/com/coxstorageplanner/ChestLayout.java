package com.coxstorageplanner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An ordered take-out list as an inventory layout, slot by slot, and the clicks that get the inventory
 * there. A withdrawal lands in the first empty inventory slot and a deposit leaves a hole, so the
 * finished inventory is the list, one slot per click, laid over the slots the plan may use: every slot
 * but the ones holding something neither list touches. Whatever sits in one of those slots and isn't
 * what belongs there goes in, an item of the list in the wrong place included, and comes out again when
 * its slot is the first empty one. The end of the list may sit further down instead: carried in order
 * below where everything before it will land, it's left there, so a rune pouch kept in the last slot stays.
 * Two neighbours of the list the wrong way round are as good as right: clicks 9 and 10 made as 10 and 9
 * leave the two swapped, and nothing is asked back for that.
 *
 * <p>A storage has only so many slots and every item takes one (a stack takes one for the lot), so not
 * everything may fit at once. Then it goes in rounds: put in what fits, take out what the holes ask
 * for, put in the next lot. With one free slot that's a swap at a time. Which deposit comes first is
 * picked by how many withdrawals it opens up. A storage with room for everything never waits: whatever
 * is in the wrong slot goes in before the next withdrawal. While it's tight, an item of the list in the wrong
 * slot isn't put back in, it's dragged to its own ({@link #moves}); and when nothing fits and no hole
 * is where its item can land, up to {@link #BATCH} items come out into whatever slots are empty, to be
 * dragged into place, which frees their storage slots for what they displace.
 */
final class ChestLayout
{
	/** A slot the plan leaves as it is. */
	static final int STAYS = -2;
	/** A slot whose item the take-out list wants somewhere else: back in, to come out in its place. */
	static final int MISPLACED = -1;
	/** How many items wait for a drag at a time, so how many come out of a full storage in one go. */
	static final int BATCH = 4;

	/** One click of the take-out list, which is one slot of the finished inventory. */
	static final class Entry
	{
		final ChestPlan.Line line;
		/** The line's position in the take-out list. */
		final int index;
		/** Whether it's a stack: one click and one slot whatever the count. */
		final boolean stack;
		/** How many the slot should hold: 1, or the stack's count. */
		final int want;

		private Entry(ChestPlan.Line line, int index, boolean stack, int want)
		{
			this.line = line;
			this.index = index;
			this.stack = stack;
			this.want = want;
		}
	}

	/** The finished inventory, in order. Only what's on you or in the storage. */
	final List<Entry> entries = new ArrayList<>();
	/**
	 * Entries before this one belong in the first slots the plan may use, entry k in the k-th. The ones
	 * from here on are carried further down already, in order, and stay where they are.
	 */
	final int head;
	/** Per entry, whether its slot holds it already. */
	final boolean[] placed;
	/** Per entry, how many a stack that's in its slot still lacks. */
	final int[] shortBy;
	/** Per take-out line, whether worn gear covers it. */
	final boolean[] worn;
	/** Per inventory slot: the put-in line that takes it, {@link #MISPLACED}, or {@link #STAYS}. */
	final int[] via;
	/** The inventory slots to empty into the storage, in slot order. */
	final List<Integer> wrong = new ArrayList<>();
	/** Those of {@link #wrong} the storage has room for right now. */
	final Set<Integer> depositNow = new LinkedHashSet<>();
	/** The entries that can be withdrawn right now, in click order. */
	final List<Integer> queue;
	/** Whether it's the turn of the withdrawals: nothing more fits, or a tight storage's round of them isn't finished. */
	final boolean withdrawing;
	/**
	 * With the storage too full for everything: the drags that put an item of the list into the slot it
	 * belongs in, as {from, to}, the next one first; whatever is in that slot swaps places with it.
	 */
	final List<int[]> moves;
	/** Whether it's the turn of the drags. */
	final boolean moving;
	/** Whether {@link #queue} is of the kind that lands in whatever slots are empty, to be dragged into place after. */
	final boolean loose;
	/** Things still have to go in, the storage is full, nothing can come out and no drag helps. */
	final boolean blocked;

	private final Set<String> stackable;
	/** The inventory slots the plan may use, in order. */
	private final int[] layout;
	/** Per entry, the inventory slot it belongs in: its own, or its neighbour's when the two are swapped. */
	private final int[] at;
	/** Per position of {@link #layout} before {@link #head}, the entry that belongs there. */
	private final int[] who;

	/**
	 * @param budgets per put-in line, how many more it takes: MAX_VALUE for all of them
	 * @param names the inventory slot by slot, null for an empty one
	 * @param storage what the storage holds, null if the client hasn't seen it (then anything is assumed to be in it)
	 * @param free storage slots left, negative for unknown
	 * @param wasWithdrawing whether the last look at this storage was in a round of withdrawals
	 * @param wasLoose whether that round was one of withdrawals into whatever slots were empty
	 */
	ChestLayout(List<ChestPlan.Line> takeOut, List<ChestPlan.Line> putIn, int[] budgets, String[] names, int[] quantities,
		Map<String, Integer> worn, Map<String, Integer> storage, int free, Set<String> stackable, boolean wasWithdrawing, boolean wasLoose)
	{
		this.stackable = stackable;
		int n = names.length;
		Map<String, Integer> carried = new HashMap<>();
		for (int s = 0; s < n; s++)
		{
			if (names[s] != null)
			{
				carried.merge(names[s], Math.max(1, quantities[s]), Integer::sum);
			}
		}
		Map<String, Integer> held = storage == null ? Collections.emptyMap() : storage;

		List<Integer> open = new ArrayList<>();
		for (int s = 0; s < n; s++)
		{
			if (names[s] == null || wanted(takeOut, names[s]) || taker(putIn, budgets, takeOut, names[s]) >= 0)
			{
				open.add(s);
			}
		}
		layout = new int[open.size()];
		for (int k = 0; k < layout.length; k++)
		{
			layout[k] = open.get(k);
		}

		Map<String, Integer> wornLeft = new HashMap<>(worn);
		Map<String, Integer> carriedLeft = new HashMap<>(carried);
		Map<String, Integer> storedLeft = storage == null ? null : new HashMap<>(storage);
		this.worn = new boolean[takeOut.size()];
		for (int i = 0; i < takeOut.size(); i++)
		{
			ChestPlan.Line line = takeOut.get(i);
			if (line.wear)
			{
				continue;
			}
			int want = line.count - ChestProgress.take(line, wornLeft, line.count);
			if (want <= 0)
			{
				this.worn[i] = true;
				continue;
			}
			int have = ChestProgress.take(line, carriedLeft, want);
			int there = storedLeft == null ? want - have : ChestProgress.take(line, storedLeft, want - have);
			boolean stack = ChestProgress.stacks(line, stackable, carried) || ChestProgress.stacks(line, stackable, held);
			int slots = stack ? Math.min(1, have + there) : have + there;
			// what the inventory has no slot for is left out, like what's nowhere to be found
			for (int u = 0; u < slots && entries.size() < layout.length; u++)
			{
				entries.add(new Entry(line, i, stack, stack ? have + there : 1));
			}
		}

		// the end of the list, as far back as it's carried in order with room above it for the rest
		at = new int[entries.size()];
		boolean[] kept = new boolean[n];
		int head = entries.size();
		int below = layout.length;
		for (int k = entries.size() - 1; k >= 0; k--)
		{
			int found = -1;
			for (int p = below - 1; p >= k && found < 0; p--)
			{
				String name = names[layout[p]];
				if (name != null && entries.get(k).line.matches(name))
				{
					found = p;
				}
			}
			if (found < 0)
			{
				break;
			}
			at[k] = layout[found];
			kept[layout[found]] = true;
			below = found;
			head = k;
		}
		this.head = head;
		who = new int[head];
		for (int k = 0; k < head; k++)
		{
			who[k] = k;
		}
		// two neighbours the wrong way round: one of them sits in the other's slot, and neither slot holds its own
		for (int k = 0; k + 1 < head; k++)
		{
			String here = names[layout[k]];
			String next = names[layout[k + 1]];
			Entry first = entries.get(k);
			Entry second = entries.get(k + 1);
			if ((here != null && first.line.matches(here)) || (next != null && second.line.matches(next)))
			{
				continue;
			}
			if ((here != null && second.line.matches(here)) || (next != null && first.line.matches(next)))
			{
				who[k] = k + 1;
				who[k + 1] = k;
				k++;
			}
		}
		for (int p = 0; p < head; p++)
		{
			at[who[p]] = layout[p];
		}

		placed = new boolean[entries.size()];
		shortBy = new int[entries.size()];
		for (int k = 0; k < entries.size(); k++)
		{
			Entry entry = entries.get(k);
			int s = at[k];
			if (names[s] != null && entry.line.matches(names[s]))
			{
				placed[k] = true;
				if (entry.stack && quantities[s] < entry.want)
				{
					int more = entry.want - quantities[s];
					shortBy[k] = storage == null ? more : Math.min(more, ChestProgress.count(entry.line, storage));
				}
			}
		}

		via = new int[n];
		Arrays.fill(via, STAYS);
		int[] left = budgets.clone();
		// where the list goes, anything that isn't what belongs there goes in
		for (int k = 0; k < head; k++)
		{
			int s = at[k];
			if (names[s] != null && !placed[k])
			{
				int line = taker(putIn, left, takeOut, names[s]);
				via[s] = line >= 0 ? line : MISPLACED;
				charge(left, line, quantities[s]);
			}
		}
		// past it, only what the put-in list takes
		for (int k = head; k < layout.length; k++)
		{
			int s = layout[k];
			int line = names[s] == null || kept[s] ? -1 : taker(putIn, left, takeOut, names[s]);
			if (line >= 0)
			{
				via[s] = line;
				charge(left, line, quantities[s]);
			}
		}
		// and what an open slot wants that the storage won't have even then
		if (storage != null)
		{
			Map<String, Integer> pool = new HashMap<>(storage);
			for (int s = 0; s < n; s++)
			{
				if (via[s] != STAYS)
				{
					pool.merge(names[s], Math.max(1, quantities[s]), Integer::sum);
				}
			}
			for (int k = 0; k < entries.size(); k++)
			{
				if (placed[k] || ChestProgress.take(entries.get(k).line, pool, 1) > 0)
				{
					continue;
				}
				for (int j = head; j < layout.length; j++)
				{
					int s = layout[j];
					if (names[s] != null && !kept[s] && via[s] == STAYS && entries.get(k).line.matches(names[s]))
					{
						via[s] = MISPLACED;
						break;
					}
				}
			}
		}
		for (int s = 0; s < n; s++)
		{
			if (via[s] != STAYS)
			{
				wrong.add(s);
			}
		}

		List<Integer> clicks = fill(names.clone(), storage == null ? null : new HashMap<>(storage));
		for (int k = 0; k < entries.size(); k++)
		{
			if (shortBy[k] > 0)
			{
				clicks.add(k);
			}
		}
		Collections.sort(clicks);

		boolean tight = storage != null && free >= 0 && newSlots(names, storage) > free;
		Set<Integer> dragged = new LinkedHashSet<>();
		Set<Integer> targets = new LinkedHashSet<>();
		moves = Collections.unmodifiableList(tight ? drags(names, dragged, targets) : new ArrayList<>());
		if (tight)
		{
			fit(names, quantities, storage, free, dragged);
		}
		else
		{
			depositNow.addAll(wrong);
		}
		boolean room = !depositNow.isEmpty();
		// nothing fits and no hole is one its item can land in: out into the empty slots, a few at a time
		// and once that has begun, until BATCH of them wait for their drag
		boolean anywhere = tight && clicks.isEmpty() && (moves.isEmpty() ? !room : wasWithdrawing && wasLoose);
		if (anywhere)
		{
			clicks = loose(names, storage, targets, BATCH - moves.size());
		}
		loose = anywhere && !clicks.isEmpty();
		queue = Collections.unmodifiableList(clicks);

		// only a storage that's tight goes in rounds; with room, what's out of place goes in as soon as it is
		boolean out = wrong.isEmpty() || (tight && wasWithdrawing && !queue.isEmpty());
		moving = !out && !moves.isEmpty();
		withdrawing = out || (!moving && !room);
		blocked = withdrawing && !wrong.isEmpty() && queue.isEmpty();
	}

	/**
	 * The drags that set the list right without the storage: for each slot of the list that lacks its
	 * item, one carried out of place. A swap that sets both slots right comes first.
	 *
	 * @param dragged filled with the slots the drags start from
	 * @param targets filled with the entries the drags are for
	 */
	private List<int[]> drags(String[] names, Set<Integer> dragged, Set<Integer> targets)
	{
		List<int[]> swaps = new ArrayList<>();
		List<int[]> rest = new ArrayList<>();
		for (int k = 0; k < head; k++)
		{
			int to = at[k];
			if (placed[k])
			{
				continue;
			}
			int from = -1;
			boolean swap = false;
			for (int s : wrong)
			{
				if (s == to || dragged.contains(s) || !entries.get(k).line.matches(names[s]))
				{
					continue;
				}
				boolean both = false;
				for (int j = 0; j < head && names[to] != null; j++)
				{
					both |= at[j] == s && entries.get(j).line.matches(names[to]);
				}
				if (from < 0 || (both && !swap))
				{
					from = s;
					swap = both;
				}
			}
			if (from >= 0)
			{
				dragged.add(from);
				targets.add(k);
				(swap ? swaps : rest).add(new int[]{from, to});
			}
		}
		swaps.addAll(rest);
		return swaps;
	}

	/**
	 * Withdrawals that land wherever the first empty slots are, for a storage with no room: the first
	 * entries of the list that aren't in place, aren't carried and are in the storage.
	 */
	private List<Integer> loose(String[] names, Map<String, Integer> storage, Set<Integer> targets, int most)
	{
		int empty = 0;
		for (String name : names)
		{
			empty += name == null ? 1 : 0;
		}
		Map<String, Integer> stored = new HashMap<>(storage);
		List<Integer> picks = new ArrayList<>();
		for (int k = 0; k < head && picks.size() < Math.min(most, empty); k++)
		{
			Entry entry = entries.get(k);
			if (placed[k] || targets.contains(k) || (entry.stack && holds(names, entry.line)))
			{
				continue;
			}
			String name = fullest(entry.line, stored);
			if (name != null)
			{
				stored.merge(name, -(entry.stack ? entry.want : 1), Integer::sum);
				picks.add(k);
			}
		}
		return picks;
	}

	/** Nothing left to put in, and nothing more that can come out. */
	boolean done()
	{
		return wrong.isEmpty() && queue.isEmpty();
	}

	/** How many storage slots putting all of {@link #wrong} in would take. */
	private int newSlots(String[] names, Map<String, Integer> storage)
	{
		Set<String> stacks = new LinkedHashSet<>(storage.keySet());
		int slots = 0;
		for (int s : wrong)
		{
			slots += cost(names[s], stacks);
			stacks.add(names[s]);
		}
		return slots;
	}

	/** Storage slots an item takes: one, or none for a stackable one the storage holds a stack of. */
	private int cost(String name, Set<String> stored)
	{
		return stackable.contains(name) && stored.contains(name) ? 0 : 1;
	}

	/**
	 * Picks the deposits the storage has room for, each time the one that lets the most withdrawals
	 * follow, the lowest slot among equals. So with one free slot the item in the way of something
	 * that's in the storage goes before one whose own replacement is still in the inventory.
	 *
	 * @param dragged slots that get dragged into place instead
	 */
	private void fit(String[] names, int[] quantities, Map<String, Integer> storage, int free, Set<Integer> dragged)
	{
		String[] slots = names.clone();
		Map<String, Integer> stored = new HashMap<>(storage);
		int room = free;
		List<Integer> rest = new ArrayList<>(wrong);
		rest.removeAll(dragged);
		while (!rest.isEmpty())
		{
			int best = -1;
			int bestScore = -1;
			for (int s : rest)
			{
				if (cost(names[s], stored.keySet()) > room)
				{
					continue;
				}
				String[] after = slots.clone();
				after[s] = null;
				Map<String, Integer> has = new HashMap<>(stored);
				has.merge(names[s], Math.max(1, quantities[s]), Integer::sum);
				int score = fill(after, has).size();
				if (score > bestScore)
				{
					best = s;
					bestScore = score;
				}
			}
			if (best < 0)
			{
				break;
			}
			room -= cost(names[best], stored.keySet());
			stored.merge(names[best], Math.max(1, quantities[best]), Integer::sum);
			slots[best] = null;
			rest.remove((Integer) best);
			depositNow.add(best);
		}
	}

	/**
	 * Plays the withdrawals forward: the first empty slot takes its entry if the storage has it, and so
	 * on until a slot's entry isn't there or the list is through. Changes both arguments.
	 *
	 * @param stored null for a storage the client hasn't seen, which is taken to hold everything
	 * @return the entries withdrawn, in order
	 */
	private List<Integer> fill(String[] slots, Map<String, Integer> stored)
	{
		List<Integer> clicks = new ArrayList<>();
		for (int p = 0; p < layout.length; p++)
		{
			if (slots[layout[p]] != null)
			{
				continue;
			}
			if (p >= head)
			{
				break;
			}
			int k = who[p];
			Entry entry = entries.get(k);
			// a stack carried elsewhere would swallow the withdrawal where it sits
			if (entry.stack && holds(slots, entry.line))
			{
				break;
			}
			String name = stored == null ? entry.line.name : fullest(entry.line, stored);
			if (name == null)
			{
				break;
			}
			if (stored != null)
			{
				int rest = stored.get(name) - (entry.stack ? entry.want : 1);
				if (rest > 0)
				{
					stored.put(name, rest);
				}
				else
				{
					stored.remove(name);
				}
			}
			slots[layout[p]] = name;
			clicks.add(k);
		}
		return clicks;
	}

	private static boolean holds(String[] slots, ChestPlan.Line line)
	{
		for (String name : slots)
		{
			if (name != null && line.matches(name))
			{
				return true;
			}
		}
		return false;
	}

	/** The fullest dose of a line's item in the storage, null when it holds none. */
	private static String fullest(ChestPlan.Line line, Map<String, Integer> stored)
	{
		String best = null;
		for (Map.Entry<String, Integer> e : stored.entrySet())
		{
			if (e.getValue() > 0 && line.matches(e.getKey()) && (best == null || ChestProgress.dose(e.getKey()) > ChestProgress.dose(best)))
			{
				best = e.getKey();
			}
		}
		return best;
	}

	/** Whether the take-out list has a line for an item to carry (not to wear). */
	private static boolean wanted(List<ChestPlan.Line> takeOut, String name)
	{
		for (ChestPlan.Line line : takeOut)
		{
			if (!line.wear && line.matches(name))
			{
				return true;
			}
		}
		return false;
	}

	/** The first put-in line that still takes an item, -1 for none. */
	private static int taker(List<ChestPlan.Line> putIn, int[] left, List<ChestPlan.Line> takeOut, String name)
	{
		for (int j = 0; j < putIn.size(); j++)
		{
			ChestPlan.Line line = putIn.get(j);
			if (left[j] > 0 && (line.everything || (line.everythingElse ? !ChestProgress.keeps(takeOut, name) : line.matches(name))))
			{
				return j;
			}
		}
		return -1;
	}

	private static void charge(int[] left, int line, int quantity)
	{
		if (line >= 0 && left[line] != Integer.MAX_VALUE)
		{
			left[line] -= Math.max(1, quantity);
		}
	}
}

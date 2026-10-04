package com.coxstorageplanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What to do at one storage unit: things to put in and things to take out, in order.
 * A line is an item name, matched from its start so "Xeric's aid" covers every dose, or a pattern
 * with * and ? like "*chinchompa", with an optional count like "Stinkhorn mushroom, 3".
 * "Ayak | Sang* staff*" takes either, the first by preference; the count, if any, goes at the end and covers the lot.
 * "Venator bow | *chinchompa & Twisted buckler" is the bow, or else the other two: "&" puts several items
 * on a line, each with a count of its own if it wants one.
 * "everything" deposits it all, "everything else" deposits whatever the take-out list doesn't keep.
 * A take-out line starting with "wear" is gear to put on, done once it's worn.
 */
public final class ChestPlan
{
	public static final String EVERYTHING = "everything";
	public static final String EVERYTHING_ELSE = "everything else";
	public static final int MAX_LINES = 40;
	public static final int MAX_LINE = 100;

	/** One line of a list, parsed. */
	public static final class Line
	{
		public final String text;
		public final String name;
		public final int count;
		/** Whether the line says how many with " xN"; a deposit without it means all of them. */
		public final boolean counted;
		public final boolean everything;
		/** A deposit of whatever the take-out list doesn't ask for. */
		public final boolean everythingElse;
		/** A take-out that's done when the item is worn, not carried. */
		public final boolean wear;
		/** The names this line takes, one unless it says "A | B" or "A & B". */
		private final List<String> names;
		/** Compiled forms of the names, null for a plain name; same order as names. */
		private final List<Pattern> patterns;
		/** The choices of an "A | B & C" line, the preferred one first, each as the items it takes; empty for a line of one item. */
		final List<List<Line>> options;
		/** Whether the choices are one item each under one count, so that any mix of them makes up the number. */
		final boolean either;

		Line(String text)
		{
			this.text = text;
			String lower = text.toLowerCase(Locale.ROOT);
			everything = lower.equals(EVERYTHING) || lower.equals("*") || lower.equals("all");
			everythingElse = lower.equals(EVERYTHING_ELSE) || lower.equals("the rest") || lower.equals("rest");
			boolean wear = false;
			String name = text;
			for (String prefix : new String[]{"wear ", "equip ", "wield "})
			{
				if (lower.startsWith(prefix))
				{
					wear = true;
					name = text.substring(prefix.length()).trim();
					lower = lower.substring(prefix.length()).trim();
					break;
				}
			}
			this.wear = wear;
			String body = name;
			int count = 1;
			boolean counted = false;
			// "Name, 3", or the older "Name x3"
			int comma = lower.lastIndexOf(',');
			int x = lower.lastIndexOf(" x");
			int cut = comma > 0 ? comma : x;
			int numberAt = comma > 0 ? comma + 1 : x + 2;
			if (cut > 0)
			{
				try
				{
					count = Math.max(1, Integer.parseInt(lower.substring(numberAt).trim()));
					name = name.substring(0, cut).trim();
					counted = true;
				}
				catch (NumberFormatException e)
				{
					// the comma or "x" was part of the name
				}
			}
			String prefix = wear ? "wear " : "";
			List<List<Line>> options = new ArrayList<>();
			boolean either = false;
			if (body.contains("|") || body.contains("&"))
			{
				// a count of its own on any item but the last, or an "&": the counts are each item's own
				boolean own = body.contains("&");
				String[] choices = body.split("\\|");
				for (int i = 0; i < choices.length - 1 && !own; i++)
				{
					own = new Line(choices[i].trim()).counted;
				}
				either = !own;
				for (String choice : (own ? body : name).split("\\|"))
				{
					List<Line> items = new ArrayList<>();
					for (String item : choice.split("&"))
					{
						if (!item.trim().isEmpty())
						{
							items.add(new Line(prefix + item.trim() + (counted && !own ? ", " + count : "")));
						}
					}
					if (!items.isEmpty())
					{
						options.add(items);
					}
				}
				if (own)
				{
					name = body;
					count = 1;
					counted = false;
				}
			}
			this.options = options;
			this.either = either;
			this.name = name;
			this.count = count;
			this.counted = counted;
			names = new ArrayList<>();
			patterns = new ArrayList<>();
			for (List<Line> items : options)
			{
				for (Line item : items)
				{
					names.add(item.name);
					patterns.add(item.patterns.get(0));
				}
			}
			if (names.isEmpty())
			{
				names.add(name);
				patterns.add(name.contains("*") || name.contains("?") ? glob(name) : null);
			}
		}

		/** Whether the line is a plain name: no wildcards, no "|", no "&". */
		boolean plain()
		{
			return names.size() == 1 && patterns.get(0) == null;
		}

		private static Pattern glob(String name)
		{
			StringBuilder regex = new StringBuilder();
			for (char c : name.toCharArray())
			{
				regex.append(c == '*' ? ".*" : c == '?' ? "." : Pattern.quote(String.valueOf(c)));
			}
			return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
		}

		/** Whether an item with this name is what the line asks for. */
		public boolean matches(String itemName)
		{
			if (everything || everythingElse)
			{
				return true;
			}
			if (itemName == null)
			{
				return false;
			}
			String lower = itemName.toLowerCase(Locale.ROOT);
			for (int i = 0; i < names.size(); i++)
			{
				Pattern pattern = patterns.get(i);
				if (pattern != null ? pattern.matcher(itemName).matches() : lower.startsWith(names.get(i).toLowerCase(Locale.ROOT)))
				{
					return true;
				}
			}
			return false;
		}

		@Override
		public String toString()
		{
			return text;
		}
	}

	private String key;
	private String name;
	private List<String> deposit = new ArrayList<>();
	private List<String> withdraw = new ArrayList<>();
	private boolean ordered;

	public ChestPlan()
	{
	}

	public ChestPlan(String key, String name)
	{
		this.key = key;
		this.name = name;
	}

	public String getKey()
	{
		return key;
	}

	public String getName()
	{
		return name == null ? "" : name;
	}

	public void setName(String name)
	{
		this.name = name == null ? "" : name.trim();
	}

	public boolean isOrdered()
	{
		return ordered;
	}

	public void setOrdered(boolean ordered)
	{
		this.ordered = ordered;
	}

	public List<String> getDeposit()
	{
		return deposit == null ? deposit = new ArrayList<>() : deposit;
	}

	public List<String> getWithdraw()
	{
		return withdraw == null ? withdraw = new ArrayList<>() : withdraw;
	}

	/** Replaces a list with the non-empty lines of some typed text. */
	public static List<String> lines(String text)
	{
		List<String> lines = new ArrayList<>();
		if (text == null)
		{
			return lines;
		}
		for (String line : text.split("\n"))
		{
			line = line.trim();
			if (!line.isEmpty() && lines.size() < MAX_LINES)
			{
				lines.add(line.length() > MAX_LINE ? line.substring(0, MAX_LINE) : line);
			}
		}
		return lines;
	}

	public static List<Line> parse(List<String> lines)
	{
		List<Line> parsed = new ArrayList<>();
		for (String line : lines)
		{
			for (String item : items(line))
			{
				parsed.add(new Line(item));
			}
		}
		return parsed;
	}

	/**
	 * The items on a line: one, unless commas were used to list several. A comma is for a number
	 * ("Xeric's aid, 3"), so one followed by anything but a number starts the next item: "Overload, Xeric's aid, 3"
	 * is an Overload and three aids.
	 */
	static List<String> items(String line)
	{
		List<String> items = new ArrayList<>();
		StringBuilder item = new StringBuilder();
		for (String part : line.split(",", -1))
		{
			// a number, which may go on with the next choice: "*chinchompa, 500 & Twisted buckler"
			if (item.length() > 0 && !part.trim().isEmpty() && !part.trim().matches("\\d+(\\s*[&|].*)?"))
			{
				items.add(item.toString().trim());
				item.setLength(0);
			}
			item.append(item.length() == 0 ? "" : ",").append(part);
		}
		items.add(item.toString().trim());
		return items;
	}

	/**
	 * Adds to or takes from the line for an item, keeping the count in the "Name, N" suffix.
	 * Only a plain line for the item's base name is touched, never a wildcard, an "A | B" or an "A & B"; the line is
	 * added at the end when there is none and dropped when its count reaches zero.
	 *
	 * @return whether the lines changed
	 */
	public static boolean mark(List<String> lines, String itemName, int delta)
	{
		String name = baseName(itemName);
		for (int i = 0; i < lines.size(); i++)
		{
			Line line = new Line(lines.get(i));
			if (line.plain() && !line.everything && line.name.equalsIgnoreCase(name))
			{
				int count = line.count + delta;
				if (count <= 0)
				{
					lines.remove(i);
				}
				else
				{
					lines.set(i, count > 1 ? name + ", " + count : name);
				}
				return true;
			}
		}
		if (delta > 0 && lines.size() < MAX_LINES)
		{
			lines.add(delta > 1 ? name + ", " + delta : name);
			return true;
		}
		return false;
	}

	/** "Xeric's aid(4)" and "Toxic blowpipe (charged)" become "Xeric's aid" and "Toxic blowpipe". */
	static String baseName(String itemName)
	{
		int paren = itemName.indexOf('(');
		return paren > 0 ? itemName.substring(0, paren).trim() : itemName.trim();
	}
}

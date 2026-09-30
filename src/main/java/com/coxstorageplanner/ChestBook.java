package com.coxstorageplanner;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every chest plan, in the order the chests were first opened. Stored as a JSON list. */
public final class ChestBook
{
	public static final int MAX_CHESTS = 40;

	private final Map<String, ChestPlan> chests = new LinkedHashMap<>();

	public static ChestBook parse(String json, Gson gson)
	{
		ChestBook book = new ChestBook();
		if (json == null || json.isEmpty())
		{
			return book;
		}
		try
		{
			List<ChestPlan> plans = gson.fromJson(json, new TypeToken<List<ChestPlan>>()
			{
			}.getType());
			if (plans != null)
			{
				for (ChestPlan plan : plans)
				{
					if (plan != null && plan.getKey() != null && book.chests.size() < MAX_CHESTS)
					{
						book.chests.put(plan.getKey(), plan);
					}
				}
			}
		}
		catch (JsonSyntaxException e)
		{
			// a broken config entry: start over rather than crash
		}
		return book;
	}

	public synchronized String encode(Gson gson)
	{
		return gson.toJson(new ArrayList<>(chests.values()));
	}

	public synchronized ChestPlan get(String key)
	{
		return key == null ? null : chests.get(key);
	}

	/** The plan for a key, made with the given name if there wasn't one. Null when full. */
	public synchronized ChestPlan getOrCreate(String key, String name)
	{
		ChestPlan plan = chests.get(key);
		if (plan == null && chests.size() < MAX_CHESTS)
		{
			plan = new ChestPlan(key, name);
			chests.put(key, plan);
		}
		return plan;
	}

	public synchronized boolean remove(String key)
	{
		return chests.remove(key) != null;
	}

	public synchronized List<ChestPlan> all()
	{
		return Collections.unmodifiableList(new ArrayList<>(chests.values()));
	}

	/** Deep copy for a snapshot the Swing thread can read while the plans change. */
	public synchronized ChestBook copy(Gson gson)
	{
		return parse(encode(gson), gson);
	}
}

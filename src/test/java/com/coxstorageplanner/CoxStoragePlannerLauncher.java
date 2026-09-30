package com.coxstorageplanner;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CoxStoragePlannerLauncher
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CoxStoragePlannerPlugin.class);
		RuneLite.main(args);
	}
}

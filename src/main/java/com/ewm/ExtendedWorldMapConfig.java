package com.ewm;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("extendedworldmap")
public interface ExtendedWorldMapConfig extends Config
{
	@ConfigItem(
		keyName = "cacheBudgetMB",
		name = "Cache budget (MB)",
		description = "RAM budget for map region tiles. Trade RAM usage for smoother viewing."
	)
	default int cacheBudgetMB()
	{
		return 512;
	}
}

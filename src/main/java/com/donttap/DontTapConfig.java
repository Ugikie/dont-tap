package com.donttap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(DontTapConfig.GROUP)
public interface DontTapConfig extends Config
{
	String GROUP = "donttap";

	@ConfigItem(
		keyName = "countdown",
		name = "Show countdown",
		description = "Show the mode's instructions and a 3-2-1 countdown before each round. Turn off to start rounds instantly",
		position = 1
	)
	default boolean countdown()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tapSounds",
		name = "Tap sounds",
		description = "Play a short tick on every hit and a buzz when you tap a white tile",
		position = 2
	)
	default boolean tapSounds()
	{
		return false;
	}

	@Range(
		max = 100
	)
	@ConfigItem(
		keyName = "soundVolume",
		name = "Volume",
		description = "Volume of the tap sounds",
		position = 3
	)
	default int soundVolume()
	{
		return 50;
	}
}

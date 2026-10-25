package com.donttap;

import com.donttap.game.GameMode;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

// stored per RuneLite profile rather than per account: practice scores belong to the player
@Singleton
public class BestScores
{
	private static final String KEY_PREFIX = "best.";

	private final ConfigManager configManager;

	@Inject
	BestScores(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	public Long get(GameMode mode)
	{
		return configManager.getConfiguration(DontTapConfig.GROUP, KEY_PREFIX + mode.key(), Long.class);
	}

	// returns true when the score beats the stored best (or there was none)
	public boolean submit(GameMode mode, long score)
	{
		final Long best = get(mode);
		if (best != null && !mode.isBetter(score, best))
		{
			return false;
		}

		configManager.setConfiguration(DontTapConfig.GROUP, KEY_PREFIX + mode.key(), score);
		return true;
	}
}

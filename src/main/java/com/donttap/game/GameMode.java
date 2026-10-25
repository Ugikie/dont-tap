package com.donttap.game;

import java.util.Locale;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GameMode
{
	ENDURANCE("Endurance", "10 seconds for every 40 taps", false),
	PATTERN("Pattern", "Clear all the patterns", true),
	FRENZY("Frenzy", "30 seconds, nothing more", false);

	private final String displayName;
	private final String instructions;
	// pattern is scored on time taken, the others on points
	private final boolean lowerIsBetter;

	public TapGame create(Random random, boolean countdown)
	{
		switch (this)
		{
			case ENDURANCE:
				return new EnduranceGame(random, countdown);
			case PATTERN:
				return new PatternGame(random, countdown);
			default:
				return new FrenzyGame(random, countdown);
		}
	}

	public String key()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	public boolean isBetter(long score, long best)
	{
		return lowerIsBetter ? score < best : score > best;
	}

	// pattern scores are milliseconds
	public String formatScore(long score)
	{
		return lowerIsBetter ? String.format("%.2fs", score / 1000.0) : Long.toString(score);
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}

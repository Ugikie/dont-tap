package com.donttap.game;

import java.util.Random;

public class PatternGame extends TapGame
{
	public static final int PATTERN_COUNT = 15;
	static final int PATTERN_SIZE = 4;

	private double elapsed;
	private int patternsLeft = PATTERN_COUNT;

	PatternGame(Random random, boolean countdown)
	{
		super(random, countdown);
	}

	@Override
	public GameMode getMode()
	{
		return GameMode.PATTERN;
	}

	@Override
	public long getScore()
	{
		return Math.round(elapsed * 1000);
	}

	@Override
	public double getTimeValue()
	{
		return elapsed;
	}

	@Override
	public boolean isSuccess()
	{
		return patternsLeft == 0;
	}

	public int getPatternsLeft()
	{
		return patternsLeft;
	}

	@Override
	protected void onLaunch()
	{
		lightRandomTiles(PATTERN_SIZE, -1);
	}

	@Override
	// scored on time, so a hit is worth no points and shows no popup
	protected int onHit(int index)
	{
		raiseGauge();
		if (allClear())
		{
			if (--patternsLeft > 0)
			{
				lightRandomTiles(PATTERN_SIZE, -1);
			}
			else
			{
				end("All clear");
			}
		}
		return 0;
	}

	@Override
	protected void update(double seconds)
	{
		elapsed += seconds;
	}
}

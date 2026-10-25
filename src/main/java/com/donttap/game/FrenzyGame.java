package com.donttap.game;

import java.util.Random;

public class FrenzyGame extends TapGame
{
	static final double ROUND_SECONDS = 30;
	static final int MAX_MULTIPLIER = 5;
	private static final int STARTING_TILES = 3;

	private double timeLeft = ROUND_SECONDS;
	private int score;

	FrenzyGame(Random random, boolean countdown)
	{
		super(random, countdown);
	}

	@Override
	public GameMode getMode()
	{
		return GameMode.FRENZY;
	}

	@Override
	public long getScore()
	{
		return score;
	}

	@Override
	public double getTimeValue()
	{
		return timeLeft;
	}

	// keeping a fast streak going fills the gauge, and a fuller gauge is worth more per tap
	public int getMultiplier()
	{
		return Math.max(1, (int) Math.ceil(MAX_MULTIPLIER * gauge));
	}

	@Override
	protected void onLaunch()
	{
		lightRandomTiles(STARTING_TILES, -1);
	}

	@Override
	protected int onHit(int index)
	{
		// worth is decided before this hit raises the gauge
		final int points = getMultiplier();
		score += points;
		lightRandom(index);
		raiseGauge();
		return points;
	}

	@Override
	protected void update(double seconds)
	{
		timeLeft -= seconds;
		if (timeLeft <= 0)
		{
			timeLeft = 0;
			end(TIMEOUT_REASON);
		}
	}
}

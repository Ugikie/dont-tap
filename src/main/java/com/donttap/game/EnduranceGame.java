package com.donttap.game;

import java.util.Arrays;
import java.util.Random;

class EnduranceGame extends TapGame
{
	static final double REFILL_SECONDS = 10;
	static final int TAPS_PER_REFILL = 40;
	static final int STARTING_TILES = 3;

	// power-up tiles fill in over this window rather than all at once
	private static final double POWER_UP_STAGGER_SECONDS = 0.5;
	private static final double NOT_PENDING = -1;

	private final double[] pendingLight = new double[TILE_COUNT];
	private double timeLeft = REFILL_SECONDS;
	private int tapsToRefill = TAPS_PER_REFILL;
	private boolean powerUp;
	private int score;

	EnduranceGame(Random random, boolean countdown)
	{
		super(random, countdown);
		Arrays.fill(pendingLight, NOT_PENDING);
	}

	@Override
	public GameMode getMode()
	{
		return GameMode.ENDURANCE;
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

	boolean isPowerUp()
	{
		return powerUp;
	}

	@Override
	protected void onLaunch()
	{
		lightRandomTiles(STARTING_TILES, -1);
	}

	@Override
	protected int onHit(int index)
	{
		score++;
		if (powerUp)
		{
			if (allClear() && !anyPending())
			{
				powerUp = false;
				lightRandomTiles(STARTING_TILES, index);
			}
		}
		else
		{
			lightRandom(index);
			raiseGauge();
			if (gauge >= 1)
			{
				startPowerUp();
			}
		}

		if (--tapsToRefill <= 0)
		{
			timeLeft += REFILL_SECONDS;
			tapsToRefill = TAPS_PER_REFILL;
			showBanner("+10 seconds");
		}
		return 1;
	}

	@Override
	protected void update(double seconds)
	{
		for (int i = 0; i < TILE_COUNT; i++)
		{
			if (pendingLight[i] == NOT_PENDING)
			{
				continue;
			}
			pendingLight[i] -= seconds;
			if (pendingLight[i] <= 0)
			{
				pendingLight[i] = NOT_PENDING;
				light(i);
			}
		}

		timeLeft -= seconds;
		if (timeLeft <= 0)
		{
			timeLeft = 0;
			end(TIMEOUT_REASON);
		}
	}

	private void startPowerUp()
	{
		powerUp = true;
		gauge = 0;
		for (int i = 0; i < TILE_COUNT; i++)
		{
			if (!isLit(i))
			{
				pendingLight[i] = random.nextDouble() * POWER_UP_STAGGER_SECONDS;
			}
		}
		showBanner("Power up!");
	}

	private boolean anyPending()
	{
		for (double delay : pendingLight)
		{
			if (delay != NOT_PENDING)
			{
				return true;
			}
		}
		return false;
	}
}

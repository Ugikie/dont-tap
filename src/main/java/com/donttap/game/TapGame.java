package com.donttap.game;

import java.util.Random;
import lombok.Getter;

// rules mirror donttap.com: a 4x4 board, black tiles to tap, and any white tile ends the round
public abstract class TapGame
{
	public enum State
	{
		COUNTDOWN,
		PLAYING,
		OVER
	}

	public enum TapResult
	{
		HIT,
		MISS,
		IGNORED
	}

	public static final int GRID_SIZE = 4;
	public static final int TILE_COUNT = GRID_SIZE * GRID_SIZE;

	static final String MISS_REASON = "You tapped a white tile";
	static final String TIMEOUT_REASON = "Time's up";

	// one second of instructions, then 3-2-1
	private static final double COUNTDOWN_SECONDS = 4;
	private static final double INSTRUCTION_SECONDS = 1;
	private static final double BANNER_SECONDS = 1;

	private static final double GAUGE_STEP = 0.08;
	private static final double GAUGE_DRAIN_PER_SECOND = 0.3;

	protected final Random random;
	private final boolean[] lit = new boolean[TILE_COUNT];

	@Getter
	private State state = State.COUNTDOWN;
	@Getter
	private String endReason;
	@Getter
	private String banner;
	@Getter
	private double bannerTimeLeft;
	@Getter
	protected double gauge;
	// what the last hit was worth, shown as "+N" on the tile; 0 means no popup
	@Getter
	private int lastHitPoints;

	// a game created without a countdown launches on its first tick
	private double countdownLeft;

	TapGame(Random random, boolean countdown)
	{
		this.random = random;
		this.countdownLeft = countdown ? COUNTDOWN_SECONDS : 0;
	}

	public abstract GameMode getMode();

	public abstract long getScore();

	// seconds left for timed modes, seconds elapsed for pattern
	public abstract double getTimeValue();

	public boolean isSuccess()
	{
		return true;
	}

	protected abstract void onLaunch();

	// returns the points the hit scored
	protected abstract int onHit(int index);

	protected abstract void update(double seconds);

	public void tick(double seconds)
	{
		if (bannerTimeLeft > 0)
		{
			bannerTimeLeft = Math.max(0, bannerTimeLeft - seconds);
		}

		if (state == State.COUNTDOWN)
		{
			countdownLeft -= seconds;
			if (countdownLeft <= 0)
			{
				state = State.PLAYING;
				onLaunch();
				showBanner("Tap the black tiles!");
			}
		}
		else if (state == State.PLAYING)
		{
			gauge = Math.max(0, gauge - GAUGE_DRAIN_PER_SECOND * seconds);
			update(seconds);
		}
	}

	public TapResult tap(int index)
	{
		if (state != State.PLAYING)
		{
			return TapResult.IGNORED;
		}

		if (!lit[index])
		{
			end(MISS_REASON);
			return TapResult.MISS;
		}

		lit[index] = false;
		lastHitPoints = onHit(index);
		return TapResult.HIT;
	}

	public boolean isLit(int index)
	{
		return lit[index];
	}

	public boolean isShowingInstructions()
	{
		return state == State.COUNTDOWN && countdownLeft > COUNTDOWN_SECONDS - INSTRUCTION_SECONDS;
	}

	public int getCountdownNumber()
	{
		return (int) Math.ceil(countdownLeft);
	}

	protected void end(String reason)
	{
		state = State.OVER;
		endReason = reason;
		bannerTimeLeft = 0;
	}

	protected void showBanner(String text)
	{
		banner = text;
		bannerTimeLeft = BANNER_SECONDS;
	}

	protected void raiseGauge()
	{
		gauge = Math.min(1, gauge + GAUGE_STEP);
	}

	protected void light(int index)
	{
		lit[index] = true;
	}

	// a random white tile, never the one just tapped so a target can't reappear under the cursor
	protected void lightRandom(int exclude)
	{
		int index;
		do
		{
			index = random.nextInt(TILE_COUNT);
		}
		while (index == exclude || lit[index]);
		lit[index] = true;
	}

	protected void lightRandomTiles(int count, int exclude)
	{
		for (int i = 0; i < count; i++)
		{
			lightRandom(exclude);
		}
	}

	protected boolean allClear()
	{
		for (boolean tile : lit)
		{
			if (tile)
			{
				return false;
			}
		}
		return true;
	}
}

package com.donttap.game;

import java.util.Random;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TapGameTest
{
	private static final long SEED = 42;
	private static final double EPSILON = 1e-9;

	private static TapGame launched(GameMode mode)
	{
		final TapGame game = mode.create(new Random(SEED), false);
		game.tick(0);
		return game;
	}

	private static int litCount(TapGame game)
	{
		int count = 0;
		for (int i = 0; i < TapGame.TILE_COUNT; i++)
		{
			if (game.isLit(i))
			{
				count++;
			}
		}
		return count;
	}

	private static int firstLit(TapGame game)
	{
		for (int i = 0; i < TapGame.TILE_COUNT; i++)
		{
			if (game.isLit(i))
			{
				return i;
			}
		}
		throw new AssertionError("no lit tile");
	}

	private static int firstUnlit(TapGame game)
	{
		for (int i = 0; i < TapGame.TILE_COUNT; i++)
		{
			if (!game.isLit(i))
			{
				return i;
			}
		}
		throw new AssertionError("no unlit tile");
	}

	// mid power-up the board can be momentarily empty while the staggered tiles fill in
	private static void tapThroughPowerUp(TapGame game)
	{
		if (litCount(game) == 0)
		{
			game.tick(0.5);
		}
		assertEquals(TapGame.TapResult.HIT, game.tap(firstLit(game)));
	}

	// taps exactly the tiles lit right now; whatever lights up afterwards is left alone
	private static void clearBoard(TapGame game)
	{
		for (int remaining = litCount(game); remaining > 0; remaining--)
		{
			assertEquals(TapGame.TapResult.HIT, game.tap(firstLit(game)));
		}
	}

	@Test
	public void countdownIgnoresTapsThenLaunches()
	{
		final TapGame game = GameMode.FRENZY.create(new Random(SEED), true);
		assertEquals(TapGame.State.COUNTDOWN, game.getState());
		assertTrue(game.isShowingInstructions());
		assertEquals(TapGame.TapResult.IGNORED, game.tap(0));

		game.tick(1.5);
		assertFalse(game.isShowingInstructions());
		assertEquals(3, game.getCountdownNumber());

		game.tick(2.5);
		assertEquals(TapGame.State.PLAYING, game.getState());
		assertEquals(3, litCount(game));
	}

	@Test
	public void hitLightsAnotherTile()
	{
		final TapGame game = launched(GameMode.FRENZY);
		for (int i = 0; i < 100; i++)
		{
			final int tapped = firstLit(game);
			assertEquals(TapGame.TapResult.HIT, game.tap(tapped));
			assertFalse("a new target must never appear under the cursor", game.isLit(tapped));
			assertEquals(3, litCount(game));
		}
	}

	@Test
	public void whiteTileEndsRound()
	{
		final TapGame game = launched(GameMode.FRENZY);
		assertEquals(TapGame.TapResult.MISS, game.tap(firstUnlit(game)));
		assertEquals(TapGame.State.OVER, game.getState());
		assertEquals(TapGame.MISS_REASON, game.getEndReason());
		assertEquals(TapGame.TapResult.IGNORED, game.tap(0));
	}

	@Test
	public void clearedTileCountsAsWhite()
	{
		final TapGame game = launched(GameMode.PATTERN);
		final int tile = firstLit(game);
		game.tap(tile);
		assertEquals(TapGame.TapResult.MISS, game.tap(tile));
	}

	@Test
	public void gaugeDrainsOverTime()
	{
		final TapGame game = launched(GameMode.FRENZY);
		game.tap(firstLit(game));
		game.tap(firstLit(game));
		assertEquals(0.16, game.getGauge(), EPSILON);

		game.tick(0.2);
		assertEquals(0.10, game.getGauge(), EPSILON);

		game.tick(1);
		assertEquals(0, game.getGauge(), EPSILON);
	}

	@Test
	public void enduranceAddsTimeEveryFortyTaps()
	{
		final TapGame game = launched(GameMode.ENDURANCE);
		for (int i = 0; i < EnduranceGame.TAPS_PER_REFILL - 1; i++)
		{
			tapThroughPowerUp(game);
		}
		final double before = game.getTimeValue();

		tapThroughPowerUp(game);
		assertEquals(before + EnduranceGame.REFILL_SECONDS, game.getTimeValue(), EPSILON);
		assertEquals("+10 seconds", game.getBanner());
		assertEquals(EnduranceGame.TAPS_PER_REFILL, game.getScore());
	}

	@Test
	public void enduranceTimesOutKeepingScore()
	{
		final TapGame game = launched(GameMode.ENDURANCE);
		game.tap(firstLit(game));
		game.tick(EnduranceGame.REFILL_SECONDS);
		assertEquals(TapGame.State.OVER, game.getState());
		assertEquals(TapGame.TIMEOUT_REASON, game.getEndReason());
		assertEquals(0, game.getTimeValue(), EPSILON);
		assertEquals(1, game.getScore());
		assertTrue(game.isSuccess());
	}

	@Test
	public void endurancePowerUpFillsBoardThenReseeds()
	{
		final EnduranceGame game = (EnduranceGame) launched(GameMode.ENDURANCE);
		// 0.08 per hit fills the gauge on the 13th
		while (!game.isPowerUp())
		{
			game.tap(firstLit(game));
		}
		assertEquals(0, game.getGauge(), EPSILON);
		assertEquals("Power up!", game.getBanner());

		game.tick(0.5);
		assertEquals(TapGame.TILE_COUNT, litCount(game));

		clearBoard(game);
		assertFalse(game.isPowerUp());
		assertEquals(TapGame.State.PLAYING, game.getState());
		assertEquals(EnduranceGame.STARTING_TILES, litCount(game));
	}

	@Test
	public void frenzyMultiplierFollowsGauge()
	{
		final FrenzyGame game = (FrenzyGame) launched(GameMode.FRENZY);
		final int[] expectedPoints = {1, 1, 1, 2, 2};
		long expectedScore = 0;
		for (int points : expectedPoints)
		{
			assertEquals(points, game.getMultiplier());
			game.tap(firstLit(game));
			assertEquals(points, game.getLastHitPoints());
			expectedScore += points;
			assertEquals(expectedScore, game.getScore());
		}

		while (game.getGauge() < 1)
		{
			game.tap(firstLit(game));
		}
		assertEquals(FrenzyGame.MAX_MULTIPLIER, game.getMultiplier());
	}

	@Test
	public void frenzySlowTappingStaysAtPlusOne()
	{
		// the gauge drains 0.3/s against +0.08 per hit, so under ~3.75 taps/s it never builds
		final TapGame game = launched(GameMode.FRENZY);
		for (int i = 0; i < 20; i++)
		{
			game.tick(0.3);
			game.tap(firstLit(game));
			assertEquals(1, game.getLastHitPoints());
		}
	}

	@Test
	public void hitPointsPerMode()
	{
		final TapGame endurance = launched(GameMode.ENDURANCE);
		endurance.tap(firstLit(endurance));
		assertEquals(1, endurance.getLastHitPoints());

		final TapGame pattern = launched(GameMode.PATTERN);
		pattern.tap(firstLit(pattern));
		assertEquals("pattern is scored on time and shows no popup", 0, pattern.getLastHitPoints());
	}

	@Test
	public void frenzyEndsAfterThirtySeconds()
	{
		final TapGame game = launched(GameMode.FRENZY);
		game.tick(FrenzyGame.ROUND_SECONDS - 0.1);
		assertEquals(TapGame.State.PLAYING, game.getState());
		game.tick(0.2);
		assertEquals(TapGame.State.OVER, game.getState());
		assertEquals(TapGame.TIMEOUT_REASON, game.getEndReason());
	}

	@Test
	public void patternClearsFifteenPatternsOnTime()
	{
		final PatternGame game = (PatternGame) launched(GameMode.PATTERN);
		assertEquals(PatternGame.PATTERN_SIZE, litCount(game));

		for (int pattern = 0; pattern < PatternGame.PATTERN_COUNT; pattern++)
		{
			assertEquals(PatternGame.PATTERN_COUNT - pattern, game.getPatternsLeft());
			game.tick(0.5);
			clearBoard(game);
		}

		assertEquals(TapGame.State.OVER, game.getState());
		assertTrue(game.isSuccess());
		assertEquals(7500, game.getScore());
	}

	@Test
	public void patternMissIsAFailure()
	{
		final TapGame game = launched(GameMode.PATTERN);
		game.tick(1);
		game.tap(firstUnlit(game));
		assertEquals(TapGame.State.OVER, game.getState());
		assertFalse(game.isSuccess());
	}

	@Test
	public void bestScoreComparison()
	{
		assertTrue(GameMode.ENDURANCE.isBetter(51, 50));
		assertFalse(GameMode.FRENZY.isBetter(50, 50));
		assertTrue(GameMode.PATTERN.isBetter(9_000, 9_500));
		assertFalse(GameMode.PATTERN.isBetter(9_500, 9_000));
		assertEquals("9.50s", GameMode.PATTERN.formatScore(9_500));
		assertNotEquals(GameMode.ENDURANCE.key(), GameMode.FRENZY.key());
	}
}

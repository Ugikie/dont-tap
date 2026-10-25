package com.donttap.ui;

import com.donttap.BestScores;
import com.donttap.DontTapConfig;
import com.donttap.TapSounds;
import com.donttap.game.FrenzyGame;
import com.donttap.game.GameMode;
import com.donttap.game.PatternGame;
import com.donttap.game.TapGame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.ProgressBar;

// the whole game UI as one component, so the sidebar and the pop-out window can trade it
public class GameView extends JPanel
{
	private static final int FRAME_MILLIS = 15;
	private static final int GAP = 6;
	private static final int GAUGE_MAX = 100;
	private static final float STAT_VALUE_SCALE = 1.3f;
	private static final String NO_VALUE = "-";

	// stops frantic last clicks of a round from instantly starting the next one
	private static final long RESTART_LOCKOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(600);

	private final DontTapConfig config;
	private final BestScores bestScores;
	private final TapSounds tapSounds;
	private final Random random = new Random();

	private final BoardPanel board = new BoardPanel(this::boardPressed);
	private final JLabel bestValue = statValue();
	private final JLabel progressCaption = statCaption("Score");
	private final JLabel progressValue = statValue();
	private final JLabel timeValue = statValue();
	private final ProgressBar gauge = new ProgressBar();
	private final Map<GameMode, JButton> modeButtons = new EnumMap<>(GameMode.class);
	private final JButton dockButton = new JButton();
	private final Timer timer = new Timer(FRAME_MILLIS, e -> frame());

	private GameMode mode = GameMode.ENDURANCE;
	private TapGame game;
	private Long best;
	private long lastFrame;
	private long endedAt;
	private Runnable dockToggle;

	@Inject
	GameView(DontTapConfig config, BestScores bestScores, TapSounds tapSounds)
	{
		this.config = config;
		this.bestScores = bestScores;
		this.tapSounds = tapSounds;

		setLayout(new BorderLayout(0, GAP));
		setOpaque(false);

		final JPanel stats = new JPanel(new GridLayout(1, 3));
		stats.setOpaque(false);
		stats.add(statColumn(statCaption("Best"), bestValue));
		stats.add(statColumn(progressCaption, progressValue));
		stats.add(statColumn(statCaption("Time"), timeValue));

		gauge.setMaximumValue(GAUGE_MAX);
		gauge.setForeground(ColorScheme.BRAND_ORANGE);
		gauge.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		final JPanel modes = new JPanel(new GridLayout(1, GameMode.values().length, GAP / 2, 0));
		modes.setOpaque(false);
		for (GameMode m : GameMode.values())
		{
			final JButton button = new JButton(m.getDisplayName());
			button.setFocusable(false);
			button.setToolTipText(m.getInstructions());
			button.addActionListener(e -> start(m));
			modeButtons.put(m, button);
			modes.add(button);
		}

		dockButton.setFocusable(false);
		dockButton.addActionListener(e ->
		{
			if (dockToggle != null)
			{
				dockToggle.run();
			}
		});

		final JPanel controls = new JPanel(new GridLayout(0, 1, 0, GAP / 2));
		controls.setOpaque(false);
		controls.add(gauge);
		controls.add(modes);
		controls.add(dockButton);

		add(stats, BorderLayout.NORTH);
		add(board, BorderLayout.CENTER);
		add(controls, BorderLayout.SOUTH);

		setDocked(true);
		showIdle();
	}

	void setDockToggle(Runnable dockToggle)
	{
		this.dockToggle = dockToggle;
	}

	void setDocked(boolean docked)
	{
		dockButton.setText(docked ? "Pop out" : "Return to sidebar");
		dockButton.setToolTipText(docked ? "Play in a separate, resizable window" : null);
	}

	boolean isPlaying()
	{
		return game != null && game.getState() != TapGame.State.OVER;
	}

	// drops the current round without saving it and goes back to the start card
	void abandon()
	{
		timer.stop();
		showIdle();
	}

	private void start(GameMode selected)
	{
		selectMode(selected);
		game = mode.create(random, config.countdown());
		board.setGame(game);
		board.clearMessage();
		lastFrame = System.nanoTime();
		timer.start();
		refresh();
	}

	private void showIdle()
	{
		game = null;
		selectMode(mode);
		board.setGame(null);
		board.showMessage(mode.getDisplayName(), mode.getInstructions(), null, "Click to start");
		refresh();
	}

	private void boardPressed(int index)
	{
		if (game == null)
		{
			start(mode);
			return;
		}

		switch (game.getState())
		{
			case OVER:
				if (System.nanoTime() - endedAt >= RESTART_LOCKOUT_NANOS)
				{
					start(mode);
				}
				break;
			case PLAYING:
				tap(index);
				break;
			default:
				break;
		}
	}

	private void tap(int index)
	{
		switch (game.tap(index))
		{
			case HIT:
				board.hit(index, game.getLastHitPoints());
				tapSounds.play(TapSounds.Sound.HIT);
				break;
			case MISS:
				board.miss(index);
				tapSounds.play(TapSounds.Sound.MISS);
				roundOver();
				break;
			default:
				return;
		}
		refresh();
		timer.start();
	}

	private void frame()
	{
		final long now = System.nanoTime();
		if (isPlaying())
		{
			game.tick((now - lastFrame) / (double) TimeUnit.SECONDS.toNanos(1));
			if (game.getState() == TapGame.State.OVER)
			{
				roundOver();
			}
		}
		lastFrame = now;
		refresh();

		if (!isPlaying() && !board.isAnimating())
		{
			timer.stop();
		}
	}

	private void roundOver()
	{
		endedAt = System.nanoTime();
		final long score = game.getScore();

		if (!game.isSuccess())
		{
			board.showMessage("You failed", game.getEndReason(), null, "Click to play again");
			return;
		}

		// an empty points round isn't worth recording as a best
		final boolean counts = mode.isLowerIsBetter() || score > 0;
		final boolean newBest = counts && bestScores.submit(mode, score);
		best = bestScores.get(mode);

		final String result = (mode.isLowerIsBetter() ? "Time " : "Score ") + mode.formatScore(score);
		board.showMessage(game.getEndReason(), result, newBest ? "New best!" : null, "Click to play again");
	}

	private void refresh()
	{
		bestValue.setText(best == null ? NO_VALUE : mode.formatScore(best));

		if (mode == GameMode.PATTERN)
		{
			progressCaption.setText("Left");
			progressValue.setText(Integer.toString(game instanceof PatternGame
				? ((PatternGame) game).getPatternsLeft()
				: PatternGame.PATTERN_COUNT));
		}
		else
		{
			progressCaption.setText("Score");
			progressValue.setText(game == null ? NO_VALUE : Long.toString(game.getScore()));
		}

		timeValue.setText(game == null ? NO_VALUE
			: String.format(mode.isLowerIsBetter() ? "%.2f" : "%.1f", game.getTimeValue()));

		gauge.setValue(game == null ? 0 : (int) Math.round(game.getGauge() * GAUGE_MAX));
		gauge.setCenterLabel(game instanceof FrenzyGame ? "x" + ((FrenzyGame) game).getMultiplier() : "");

		board.repaint();
	}

	private void selectMode(GameMode selected)
	{
		mode = selected;
		best = bestScores.get(mode);
		for (Map.Entry<GameMode, JButton> entry : modeButtons.entrySet())
		{
			entry.getValue().setForeground(entry.getKey() == mode ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		}
	}

	private static JPanel statColumn(JLabel caption, JLabel value)
	{
		final JPanel column = new JPanel(new BorderLayout());
		column.setOpaque(false);
		column.add(caption, BorderLayout.NORTH);
		column.add(value, BorderLayout.CENTER);
		return column;
	}

	private static JLabel statCaption(String text)
	{
		final JLabel label = new JLabel(text, SwingConstants.CENTER);
		label.setForeground(Color.GRAY);
		return label;
	}

	private static JLabel statValue()
	{
		final JLabel label = new JLabel(NO_VALUE, SwingConstants.CENTER);
		label.setForeground(Color.WHITE);
		label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() * STAT_VALUE_SCALE));
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		return label;
	}
}

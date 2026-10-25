package com.donttap.ui;

import com.donttap.game.TapGame;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import lombok.AllArgsConstructor;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ColorUtil;

// the 4x4 board plus whatever is drawn over it: countdown, banners, and the idle/result card
class BoardPanel extends JComponent
{
	private static final int GRID = TapGame.GRID_SIZE;
	private static final int PREFERRED_SIZE = 200;

	// the site's palette: white tiles, black targets, light grey grid
	private static final Color TILE_COLOR = Color.WHITE;
	private static final Color TARGET_COLOR = Color.BLACK;
	private static final Color GRID_COLOR = new Color(184, 184, 184);
	private static final Color HIT_COLOR = new Color(0, 255, 0);
	private static final Color POINTS_COLOR = Color.RED;
	private static final Color MISS_COLOR = new Color(229, 57, 53);
	private static final Color VEIL_COLOR = new Color(0, 0, 0, 200);
	// lighter so the missed tile still shows through the result card
	private static final Color MESSAGE_VEIL_COLOR = new Color(0, 0, 0, 160);
	private static final Color BANNER_BACKGROUND = new Color(0, 0, 0, 170);

	// a hit flashes green and pops its "+N" in the tile, both fading out like the site's
	private static final long HIT_FADE_NANOS = TimeUnit.MILLISECONDS.toNanos(200);
	private static final float POINTS_SCALE = 1 / 3f;
	private static final double BANNER_FADE_SECONDS = 0.3;

	// overlay text scales with the board, but never below the client's own font size
	private static final float HEADLINE_SCALE = 1 / 10f;
	private static final float BODY_SCALE = 1 / 15f;
	private static final float COUNTDOWN_SCALE = 1 / 2.5f;
	private static final float LINE_SPACING = 1.2f;

	private final IntConsumer pressListener;
	private final long[] hitAt = new long[TapGame.TILE_COUNT];
	private final int[] hitPoints = new int[TapGame.TILE_COUNT];

	private TapGame game;
	private int missedTile = -1;

	private String headline;
	private String detail;
	private String highlight;
	private String hint;

	BoardPanel(IntConsumer pressListener)
	{
		this.pressListener = pressListener;
		setPreferredSize(new Dimension(PREFERRED_SIZE, PREFERRED_SIZE));
		setMinimumSize(new Dimension(GRID * 20, GRID * 20));

		// mouse-down, not click: the site registers a tap the instant the button goes down
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (!SwingUtilities.isLeftMouseButton(e))
				{
					return;
				}

				final int index = tileAt(e.getX(), e.getY());
				if (index >= 0)
				{
					pressListener.accept(index);
				}
			}
		});
	}

	void setGame(TapGame game)
	{
		this.game = game;
		missedTile = -1;
		Arrays.fill(hitAt, 0);
		Arrays.fill(hitPoints, 0);
		repaint();
	}

	void showMessage(String headline, String detail, String highlight, String hint)
	{
		this.headline = headline;
		this.detail = detail;
		this.highlight = highlight;
		this.hint = hint;
		repaint();
	}

	void clearMessage()
	{
		showMessage(null, null, null, null);
	}

	// points of 0 flash the tile without a "+N" popup
	void hit(int index, int points)
	{
		hitAt[index] = System.nanoTime();
		hitPoints[index] = points;
	}

	void miss(int index)
	{
		missedTile = index;
	}

	boolean isAnimating()
	{
		final long now = System.nanoTime();
		for (long at : hitAt)
		{
			if (at != 0 && now - at < HIT_FADE_NANOS)
			{
				return true;
			}
		}
		return false;
	}

	private int tileSize()
	{
		return Math.min(getWidth(), getHeight()) / GRID;
	}

	private int boardX()
	{
		return (getWidth() - tileSize() * GRID) / 2;
	}

	private int boardY()
	{
		return (getHeight() - tileSize() * GRID) / 2;
	}

	private int tileAt(int x, int y)
	{
		final int tile = tileSize();
		if (tile == 0)
		{
			return -1;
		}

		final int col = Math.floorDiv(x - boardX(), tile);
		final int row = Math.floorDiv(y - boardY(), tile);
		if (col < 0 || col >= GRID || row < 0 || row >= GRID)
		{
			return -1;
		}
		return row * GRID + col;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		final int tile = tileSize();
		if (tile == 0)
		{
			return;
		}

		final Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.translate(boardX(), boardY());

		final int size = tile * GRID;
		final long now = System.nanoTime();
		paintTiles(g2, tile, now);
		paintGrid(g2, tile, size);
		paintHitPoints(g2, tile, now);

		if (headline != null)
		{
			paintMessage(g2, size);
		}
		else if (game != null && game.getState() == TapGame.State.COUNTDOWN)
		{
			paintCountdown(g2, size);
		}
		else if (game != null && game.getState() == TapGame.State.PLAYING && game.getBannerTimeLeft() > 0)
		{
			paintBanner(g2, size);
		}

		g2.dispose();
	}

	private void paintTiles(Graphics2D g2, int tile, long now)
	{
		for (int i = 0; i < TapGame.TILE_COUNT; i++)
		{
			final int x = (i % GRID) * tile;
			final int y = (i / GRID) * tile;
			g2.setColor(tileColor(i));
			g2.fillRect(x, y, tile, tile);

			// drawn over the tile rather than replacing it: a new pattern can relight a tile mid-flash
			final int alpha = hitAlpha(i, now);
			if (alpha > 0)
			{
				g2.setColor(ColorUtil.colorWithAlpha(HIT_COLOR, alpha));
				g2.fillRect(x, y, tile, tile);
			}
		}
	}

	private void paintHitPoints(Graphics2D g2, int tile, long now)
	{
		final Font font = getFont().deriveFont(Math.max(getFont().getSize2D(), tile * POINTS_SCALE));
		final FontMetrics fm = g2.getFontMetrics(font);
		g2.setFont(font);
		for (int i = 0; i < TapGame.TILE_COUNT; i++)
		{
			final int alpha = hitAlpha(i, now);
			if (alpha == 0 || hitPoints[i] == 0)
			{
				continue;
			}

			final String text = "+" + hitPoints[i];
			g2.setColor(ColorUtil.colorWithAlpha(POINTS_COLOR, alpha));
			g2.drawString(text,
				(i % GRID) * tile + (tile - fm.stringWidth(text)) / 2,
				(i / GRID) * tile + (tile - fm.getHeight()) / 2 + fm.getAscent());
		}
	}

	// 255 the instant a tile is hit, fading to 0 over the flash
	private int hitAlpha(int index, long now)
	{
		final long sinceHit = now - hitAt[index];
		if (hitAt[index] == 0 || sinceHit >= HIT_FADE_NANOS)
		{
			return 0;
		}
		return (int) (255 * (1 - (double) sinceHit / HIT_FADE_NANOS));
	}

	private Color tileColor(int index)
	{
		if (index == missedTile)
		{
			return MISS_COLOR;
		}
		return game != null && game.isLit(index) ? TARGET_COLOR : TILE_COLOR;
	}

	private static void paintGrid(Graphics2D g2, int tile, int size)
	{
		g2.setColor(GRID_COLOR);
		for (int i = 0; i <= GRID; i++)
		{
			// the closing line sits on the last pixel row/column so it stays inside the board
			final int p = Math.min(i * tile, size - 1);
			g2.drawLine(p, 0, p, size - 1);
			g2.drawLine(0, p, size - 1, p);
		}
	}

	private void paintCountdown(Graphics2D g2, int size)
	{
		g2.setColor(VEIL_COLOR);
		g2.fillRect(0, 0, size, size);

		if (game.isShowingInstructions())
		{
			final Font font = scaledFont(Font.BOLD, size, HEADLINE_SCALE);
			final List<String> lines = wrap(game.getMode().getInstructions(), g2.getFontMetrics(font), size * 9 / 10);
			final int lineHeight = Math.round(g2.getFontMetrics(font).getHeight() * LINE_SPACING);
			int y = (size - lineHeight * lines.size()) / 2;
			for (String line : lines)
			{
				y = drawCentered(g2, line, font, Color.WHITE, size, y, lineHeight);
			}
			return;
		}

		final Font font = scaledFont(Font.BOLD, size, COUNTDOWN_SCALE);
		final int lineHeight = g2.getFontMetrics(font).getHeight();
		drawCentered(g2, Integer.toString(game.getCountdownNumber()), font, ColorScheme.BRAND_ORANGE,
			size, (size - lineHeight) / 2, lineHeight);
	}

	private void paintBanner(Graphics2D g2, int size)
	{
		final Font font = scaledFont(Font.BOLD, size, BODY_SCALE);
		final FontMetrics fm = g2.getFontMetrics(font);
		final String text = game.getBanner();
		final int padding = fm.getHeight() / 2;
		final int width = Math.min(size, fm.stringWidth(text) + padding * 2);
		final int height = fm.getHeight() + padding;
		final int x = (size - width) / 2;
		final int y = (size - height) / 2;

		// fade out over the banner's last moments rather than vanishing
		final float alpha = (float) Math.min(1, game.getBannerTimeLeft() / BANNER_FADE_SECONDS);
		final Graphics2D faded = (Graphics2D) g2.create();
		faded.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
		faded.setColor(BANNER_BACKGROUND);
		faded.fillRoundRect(x, y, width, height, height, height);
		drawCentered(faded, text, font, ColorScheme.BRAND_ORANGE, size, y + padding / 2, fm.getHeight());
		faded.dispose();
	}

	private void paintMessage(Graphics2D g2, int size)
	{
		g2.setColor(MESSAGE_VEIL_COLOR);
		g2.fillRect(0, 0, size, size);

		final Font headlineFont = scaledFont(Font.BOLD, size, HEADLINE_SCALE);
		final Font bodyFont = scaledFont(Font.PLAIN, size, BODY_SCALE);
		final Font highlightFont = bodyFont.deriveFont(Font.BOLD);
		final int maxWidth = size * 9 / 10;

		final List<Line> lines = new ArrayList<>();
		addLines(lines, g2, headline, headlineFont, Color.WHITE, maxWidth);
		addLines(lines, g2, detail, bodyFont, Color.WHITE, maxWidth);
		addLines(lines, g2, highlight, highlightFont, ColorScheme.BRAND_ORANGE, maxWidth);
		addLines(lines, g2, hint, bodyFont, ColorScheme.LIGHT_GRAY_COLOR, maxWidth);

		int total = 0;
		for (Line line : lines)
		{
			total += line.height;
		}

		int y = (size - total) / 2;
		for (Line line : lines)
		{
			y = drawCentered(g2, line.text, line.font, line.color, size, y, line.height);
		}
	}

	private static void addLines(List<Line> lines, Graphics2D g2, String text, Font font, Color color, int maxWidth)
	{
		if (text == null)
		{
			return;
		}

		final FontMetrics fm = g2.getFontMetrics(font);
		final int height = Math.round(fm.getHeight() * LINE_SPACING);
		for (String part : wrap(text, fm, maxWidth))
		{
			lines.add(new Line(part, font, color, height));
		}
	}

	// draws one line centred horizontally in a band starting at top, returning the next band's top
	private static int drawCentered(Graphics2D g2, String text, Font font, Color color, int size, int top, int lineHeight)
	{
		final FontMetrics fm = g2.getFontMetrics(font);
		g2.setFont(font);
		g2.setColor(color);
		final int baseline = top + (lineHeight - fm.getHeight()) / 2 + fm.getAscent();
		g2.drawString(text, (size - fm.stringWidth(text)) / 2, baseline);
		return top + lineHeight;
	}

	private static List<String> wrap(String text, FontMetrics fm, int maxWidth)
	{
		final List<String> lines = new ArrayList<>();
		final StringBuilder line = new StringBuilder();
		for (String word : text.split(" "))
		{
			if (line.length() > 0 && fm.stringWidth(line + " " + word) > maxWidth)
			{
				lines.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0)
			{
				line.append(' ');
			}
			line.append(word);
		}
		lines.add(line.toString());
		return lines;
	}

	private Font scaledFont(int style, int boardSize, float scale)
	{
		final Font base = getFont();
		return base.deriveFont(style, Math.max(base.getSize2D(), boardSize * scale));
	}

	@AllArgsConstructor
	private static final class Line
	{
		private final String text;
		private final Font font;
		private final Color color;
		private final int height;
	}
}

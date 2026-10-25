package com.donttap.ui;

import com.donttap.DontTapConfig;
import com.donttap.DontTapPlugin;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

@Slf4j
class GameWindow extends JFrame
{
	private static final String BOUNDS_KEY = "windowBounds";
	private static final int DEFAULT_WIDTH = 380;
	private static final int DEFAULT_HEIGHT = 520;
	private static final int MIN_WIDTH = 260;
	private static final int MIN_HEIGHT = 360;

	private final ConfigManager configManager;
	private final Timer saveBoundsTimer;

	GameWindow(ConfigManager configManager, JComponent content)
	{
		this.configManager = configManager;

		setTitle("Don't Tap");
		setIconImage(ImageUtil.loadImageResource(DontTapPlugin.class, "icon.png"));
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		final JPanel contentPane = new JPanel(new BorderLayout());
		contentPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
		contentPane.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		contentPane.add(content, BorderLayout.CENTER);
		setContentPane(contentPane);

		restoreBounds();

		saveBoundsTimer = new Timer(400, e -> saveBounds());
		saveBoundsTimer.setRepeats(false);

		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				saveBoundsTimer.restart();
			}

			@Override
			public void componentMoved(ComponentEvent e)
			{
				saveBoundsTimer.restart();
			}
		});

		getRootPane().registerKeyboardAction(e -> dispose(),
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	@Override
	public void dispose()
	{
		// a move or resize just before closing hasn't been written yet
		if (saveBoundsTimer.isRunning())
		{
			saveBoundsTimer.stop();
			saveBounds();
		}
		super.dispose();
	}

	private void restoreBounds()
	{
		setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
		setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
		setLocationRelativeTo(null);

		final String saved = configManager.getConfiguration(DontTapConfig.GROUP, BOUNDS_KEY);
		if (saved == null)
		{
			return;
		}

		try
		{
			final String[] parts = saved.split(",");
			final Rectangle bounds = new Rectangle(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
				Math.max(MIN_WIDTH, Integer.parseInt(parts[2])), Math.max(MIN_HEIGHT, Integer.parseInt(parts[3])));

			// a saved position from an unplugged monitor would leave the window
			// unreachable; every connected screen counts, not just the primary one
			if (anyScreenContains(new Point(bounds.x + 40, bounds.y + 20)))
			{
				setBounds(bounds);
			}
			else
			{
				setSize(bounds.width, bounds.height);
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("ignoring malformed saved window bounds", ex);
		}
	}

	private static boolean anyScreenContains(Point p)
	{
		for (final GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			if (device.getDefaultConfiguration().getBounds().contains(p))
			{
				return true;
			}
		}
		return false;
	}

	private void saveBounds()
	{
		if (!isShowing())
		{
			return;
		}

		final Rectangle bounds = getBounds();
		configManager.setConfiguration(DontTapConfig.GROUP, BOUNDS_KEY,
			bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);
	}
}

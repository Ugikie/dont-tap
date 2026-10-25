package com.donttap.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class DontTapPanel extends PluginPanel
{
	private final GameView gameView;
	private final ConfigManager configManager;
	private final JPanel poppedOutView;

	private GameWindow window;

	@Inject
	DontTapPanel(GameView gameView, ConfigManager configManager)
	{
		super(false);
		this.gameView = gameView;
		this.configManager = configManager;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		poppedOutView = buildPoppedOutView();
		gameView.setDockToggle(this::toggleWindow);
		dock();
	}

	// a docked round would otherwise keep ticking unseen behind another sidebar tab
	@Override
	public void onDeactivate()
	{
		if (window == null && gameView.isPlaying())
		{
			gameView.abandon();
		}
	}

	public void shutDown()
	{
		gameView.abandon();
		if (window != null)
		{
			window.dispose();
		}
	}

	private void toggleWindow()
	{
		if (window != null)
		{
			window.dispose();
			return;
		}

		gameView.abandon();
		remove(gameView);
		add(poppedOutView, BorderLayout.NORTH);
		revalidate();
		repaint();

		gameView.setDocked(false);
		window = new GameWindow(configManager, gameView);
		window.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				window = null;
				gameView.abandon();
				dock();
			}
		});
		window.setVisible(true);
	}

	// the sidebar is far taller than the game, so it sits at the top instead of stretching
	private void dock()
	{
		remove(poppedOutView);
		gameView.setDocked(true);
		add(gameView, BorderLayout.NORTH);
		revalidate();
		repaint();
	}

	private JPanel buildPoppedOutView()
	{
		final JLabel label = new JLabel("Playing in a separate window", SwingConstants.CENTER);
		label.setForeground(Color.GRAY);

		final JButton bringBack = new JButton("Bring back");
		bringBack.setFocusable(false);
		bringBack.addActionListener(e -> toggleWindow());

		final JPanel buttonRow = new JPanel(new GridBagLayout());
		buttonRow.setOpaque(false);
		buttonRow.add(bringBack);

		final JPanel view = new JPanel(new GridLayout(0, 1, 0, 6));
		view.setOpaque(false);
		view.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
		view.add(label);
		view.add(buttonRow);
		return view;
	}
}

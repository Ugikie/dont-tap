package com.donttap;

import com.donttap.ui.DontTapPanel;
import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Don't Tap",
	description = "Play Don't Tap the White Tile in the sidebar to practise fast, accurate clicking",
	tags = {"click", "tap", "tile", "white", "reaction", "minigame", "practice"}
)
public class DontTapPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private TapSounds tapSounds;

	// built in startUp: swing components created before the client installs
	// its look and feel keep the default metal ui delegates forever
	private DontTapPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		panel = injector.getInstance(DontTapPanel.class);

		navButton = NavigationButton.builder()
			.tooltip("Don't Tap")
			.icon(ImageUtil.loadImageResource(getClass(), "icon.png"))
			.priority(10)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		tapSounds.reload();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel.shutDown();
		panel = null;
		tapSounds.close();
	}

	@Provides
	DontTapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DontTapConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (DontTapConfig.GROUP.equals(event.getGroup()) && "tapSounds".equals(event.getKey()))
		{
			tapSounds.reload();
		}
	}
}

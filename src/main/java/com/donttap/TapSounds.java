package com.donttap;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.audio.AudioPlayer;

@Slf4j
@Singleton
public class TapSounds
{
	@RequiredArgsConstructor
	public enum Sound
	{
		HIT("hit.wav"),
		MISS("miss.wav");

		private final String resource;
	}

	private final DontTapConfig config;
	private final AudioPlayer audioPlayer;
	private final ScheduledExecutorService executor;

	// a machine with no output device fails every tap; say so once, not at tapping speed
	private volatile boolean warned;

	@Inject
	TapSounds(DontTapConfig config, AudioPlayer audioPlayer, ScheduledExecutorService executor)
	{
		this.config = config;
		this.audioPlayer = audioPlayer;
		this.executor = executor;
	}

	public void play(Sound sound)
	{
		final int volume = config.soundVolume();
		if (!config.tapSounds() || volume <= 0)
		{
			return;
		}

		final float gainDb = (float) (20 * Math.log10(volume / 100.0));
		// opening an audio line can block, so it never happens on the event thread
		executor.execute(() ->
		{
			try
			{
				audioPlayer.play(TapSounds.class, sound.resource, gainDb);
			}
			catch (IOException | UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException ex)
			{
				if (!warned)
				{
					warned = true;
					log.warn("Unable to play tap sound", ex);
				}
			}
		});
	}
}

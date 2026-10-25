package com.donttap;

import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import lombok.extern.slf4j.Slf4j;

// AudioPlayer opens a fresh clip per call, which lags at tapping speed; these clips are
// synthesized once, kept open, and just rewound on every tap
@Slf4j
@Singleton
public class TapSounds
{
	public enum Sound
	{
		HIT,
		MISS
	}

	private static final float SAMPLE_RATE = 44_100f;
	private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
	private static final double AMPLITUDE = 0.5 * Short.MAX_VALUE;

	private static final double HIT_FREQUENCY = 1760;
	private static final int HIT_MILLIS = 30;
	private static final double HIT_DECAY = 160;

	private static final double MISS_FREQUENCY = 150;
	private static final int MISS_MILLIS = 140;
	private static final double MISS_DECAY = 18;

	private final DontTapConfig config;
	private final ScheduledExecutorService executor;

	// opened on the executor, played on the event thread
	private volatile Clip hitClip;
	private volatile Clip missClip;
	// cleared by close() so an open still queued on the executor doesn't outlive the plugin
	private volatile boolean wanted;
	private boolean unavailable;

	@Inject
	TapSounds(DontTapConfig config, ScheduledExecutorService executor)
	{
		this.config = config;
		this.executor = executor;
	}

	// opening an audio line can block, so it never happens on the event thread
	public void reload()
	{
		if (config.tapSounds())
		{
			wanted = true;
			executor.execute(this::open);
		}
		else
		{
			close();
		}
	}

	public void play(Sound sound)
	{
		final int volume = config.soundVolume();
		final Clip clip = sound == Sound.HIT ? hitClip : missClip;
		if (!config.tapSounds() || volume <= 0 || clip == null)
		{
			return;
		}

		if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN))
		{
			final FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
			final float gainDb = (float) (20 * Math.log10(volume / 100.0));
			gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), gainDb)));
		}
		clip.stop();
		clip.setFramePosition(0);
		clip.start();
	}

	public synchronized void close()
	{
		wanted = false;
		if (hitClip != null)
		{
			hitClip.close();
			hitClip = null;
		}
		if (missClip != null)
		{
			missClip.close();
			missClip = null;
		}
	}

	private synchronized void open()
	{
		if (!wanted || hitClip != null || unavailable)
		{
			return;
		}

		Clip hit = null;
		try
		{
			hit = clip(tone(HIT_FREQUENCY, HIT_MILLIS, HIT_DECAY, false));
			missClip = clip(tone(MISS_FREQUENCY, MISS_MILLIS, MISS_DECAY, true));
			hitClip = hit;
		}
		catch (LineUnavailableException | IllegalArgumentException ex)
		{
			if (hit != null)
			{
				hit.close();
			}
			// no usable output device; don't retry on every config change
			unavailable = true;
			log.warn("Tap sounds unavailable", ex);
		}
	}

	private static Clip clip(byte[] pcm) throws LineUnavailableException
	{
		final Clip clip = AudioSystem.getClip();
		clip.open(FORMAT, pcm, 0, pcm.length);
		return clip;
	}

	// a decaying sine reads as a crisp tick; a square wave at low pitch reads as a buzz
	private static byte[] tone(double frequency, int millis, double decay, boolean square)
	{
		final int samples = (int) (SAMPLE_RATE * millis / 1000);
		final byte[] pcm = new byte[samples * 2];
		for (int i = 0; i < samples; i++)
		{
			final double t = i / SAMPLE_RATE;
			final double wave = Math.sin(2 * Math.PI * frequency * t);
			final double sample = (square ? Math.signum(wave) * 0.5 : wave) * Math.exp(-decay * t);
			final short value = (short) (sample * AMPLITUDE);
			pcm[2 * i] = (byte) value;
			pcm[2 * i + 1] = (byte) (value >> 8);
		}
		return pcm;
	}
}

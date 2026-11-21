package com.claudefga.nfcuidlogger;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Erzeugt die beiden Rückmeldetöne selbst, es sind keine Audiodateien nötig:
 * ok() = zwei aufsteigende helle Töne, fail() = tiefer, absteigender Brummton.
 */
final class SoundPlayer {

    private static final int RATE = 44100;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    void ok() {
        play(new int[]{880, 1320}, new int[]{120, 200}, false);
    }

    void fail() {
        play(new int[]{300, 200}, new int[]{250, 450}, true);
    }

    void release() {
        executor.shutdown();
    }

    private void play(final int[] freqs, final int[] durationsMs, final boolean square) {
        try {
            executor.execute(() -> render(freqs, durationsMs, square));
        } catch (RejectedExecutionException ignored) {
            // App wird gerade beendet
        }
    }

    private void render(int[] freqs, int[] durationsMs, boolean square) {
        int total = 0;
        for (int ms : durationsMs) total += RATE * ms / 1000;
        short[] pcm = new short[total];
        int pos = 0;
        final int fade = RATE * 8 / 1000;
        for (int t = 0; t < freqs.length; t++) {
            int n = RATE * durationsMs[t] / 1000;
            for (int i = 0; i < n; i++) {
                double v = Math.sin(2.0 * Math.PI * freqs[t] * i / RATE);
                if (square) v = v >= 0 ? 0.6 : -0.6;
                double env = 1.0;
                if (i < fade) env = i / (double) fade;
                else if (i > n - fade) env = (n - i) / (double) fade;
                pcm[pos++] = (short) (v * env * 0.7 * Short.MAX_VALUE);
            }
        }
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(pcm, 0, pcm.length);
            track.play();
            Thread.sleep(total * 1000L / RATE + 150);
        } catch (Exception ignored) {
            // Kein Ton ist besser als ein Absturz
        } finally {
            if (track != null) track.release();
        }
    }
}

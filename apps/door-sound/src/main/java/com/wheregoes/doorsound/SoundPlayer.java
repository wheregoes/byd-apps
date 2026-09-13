package com.wheregoes.doorsound;

import android.media.AudioAttributes;
import android.media.MediaPlayer;

import java.io.File;

class SoundPlayer implements Runnable {
    private final DoorSoundService service;
    private final String path;
    private final int volume;
    private final int maxVolume;

    SoundPlayer(DoorSoundService service, String path, int volume, int maxVolume) {
        this.service = service;
        this.path = path;
        this.volume = volume;
        this.maxVolume = maxVolume;
    }

    @Override
    public void run() {
        try {
            service.releasePlayer();
            File file = new File(path);
            if (!file.exists()) {
                service.log("SoundPlayer: file NOT FOUND: " + path);
                return;
            }
            MediaPlayer mp = new MediaPlayer();
            // SONIFICATION rather than MEDIA: the old code used USAGE_MEDIA and
            // then moved STREAM_MUSIC to the event volume, which changed the
            // radio's volume and left it changed if the service died mid-sound.
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            mp.setDataSource(path);
            final float gain = maxVolume > 0
                    ? Math.min(1f, Math.max(0f, (float) volume / maxVolume))
                    : 1f;
            mp.setOnPreparedListener(p -> {
                p.setVolume(gain, gain);
                p.start();
                service.log("SoundPlayer: started OK (gain " + gain + ")");
            });
            mp.setOnCompletionListener(p -> {
                service.log("SoundPlayer: playback completed");
                p.release();
                if (service.getActivePlayer() == p) {
                    service.setActivePlayer(null);
                }
            });
            mp.setOnErrorListener((p, what, extra) -> {
                service.log("SoundPlayer: ERROR what=" + what + " extra=" + extra);
                p.release();
                if (service.getActivePlayer() == p) {
                    service.setActivePlayer(null);
                }
                return true;
            });
            // prepareAsync: this runs on the main looper, and prepare() on a
            // file the media server has to open is not main-thread safe.
            mp.prepareAsync();
            service.setActivePlayer(mp);
        } catch (Exception e) {
            service.log("SoundPlayer: EXCEPTION " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}

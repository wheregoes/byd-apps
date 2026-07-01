package com.wheregoes.doorsound;

class PatternRunner implements Runnable {
    private final AvasPlayer player;
    private final int pattern;

    PatternRunner(AvasPlayer player, int pattern) {
        this.player = player;
        this.pattern = pattern;
    }

    @Override
    public void run() {
        try {
            switch (pattern) {
                case AvasPlayer.PATTERN_DING_DONG:
                    playDingDong();
                    break;
                case AvasPlayer.PATTERN_DONG_DING:
                    playDongDing();
                    break;
                case AvasPlayer.PATTERN_TRIPLE_BEEP:
                    playTripleBeep();
                    break;
                case AvasPlayer.PATTERN_RAPID_ALT:
                    playRapidAlt();
                    break;
                case AvasPlayer.PATTERN_LONG_CHIME:
                    playLongChime();
                    break;
                case AvasPlayer.PATTERN_SHOP_CHIME:
                    playShopChime();
                    break;
                case AvasPlayer.PATTERN_ALARM:
                    playAlarm();
                    break;
                case AvasPlayer.PATTERN_FANFARE:
                    playFanfare();
                    break;
            }
        } catch (InterruptedException ignored) {
        } finally {
            player.fullStop();
            player.setPlayingDone();
        }
    }

    private void playDingDong() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(AvasPlayer.TEST_AVAS, AvasPlayer.PITCH_A);
        player.si(AvasPlayer.AVAH, 1);
        Thread.sleep(400);
        player.si(AvasPlayer.TEST_AVAS, AvasPlayer.PITCH_B);
        Thread.sleep(600);
    }

    private void playDongDing() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(AvasPlayer.TEST_AVAS, AvasPlayer.PITCH_B);
        player.si(AvasPlayer.AVAH, 1);
        Thread.sleep(600);
        player.si(AvasPlayer.TEST_AVAS, AvasPlayer.PITCH_A);
        Thread.sleep(400);
    }

    private void playTripleBeep() throws InterruptedException {
        for (int i = 0; i < 3 && player.isPlaying(); i++) {
            player.enable();
            Thread.sleep(50);
            player.si(AvasPlayer.TEST_AVAS, AvasPlayer.PITCH_A);
            player.si(AvasPlayer.AVAH, 1);
            Thread.sleep(200);
            player.fullStop();
            Thread.sleep(200);
        }
    }

    private void playRapidAlt() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(AvasPlayer.AVAH, 1);
        for (int i = 0; i < 6 && player.isPlaying(); i++) {
            player.si(AvasPlayer.TEST_AVAS, (i % 2) + 1);
            Thread.sleep(200);
        }
    }

    private void playLongChime() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(AvasPlayer.TEST_AVAS, AvasPlayer.PITCH_B);
        player.si(AvasPlayer.AVAH, 1);
        Thread.sleep(500);
        player.si(AvasPlayer.TEST_AVAS, AvasPlayer.PITCH_A);
        Thread.sleep(800);
    }

    private void playShopChime() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(AvasPlayer.AVAH, 1);
        note(AvasPlayer.PITCH_A, 200);
        rest(120);
        note(AvasPlayer.PITCH_A, 200);
        rest(120);
        note(AvasPlayer.PITCH_B, 300);
        rest(120);
        note(AvasPlayer.PITCH_B, 400);
    }

    private void playAlarm() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(AvasPlayer.AVAH, 1);
        for (int i = 0; i < 4 && player.isPlaying(); i++) {
            note(AvasPlayer.PITCH_A, 300);
            note(AvasPlayer.PITCH_B, 300);
        }
    }

    private void playFanfare() throws InterruptedException {
        player.enable();
        Thread.sleep(100);
        player.si(AvasPlayer.AVAH, 1);
        note(AvasPlayer.PITCH_A, 150);
        rest(80);
        note(AvasPlayer.PITCH_A, 150);
        rest(80);
        note(AvasPlayer.PITCH_A, 150);
        rest(80);
        note(AvasPlayer.PITCH_B, 300);
        rest(100);
        note(AvasPlayer.PITCH_B, 600);
    }

    private void note(int pitch, int ms) throws InterruptedException {
        player.si(AvasPlayer.TEST_AVAS, pitch);
        Thread.sleep(ms);
    }

    private void rest(int ms) throws InterruptedException {
        player.si(AvasPlayer.TEST_AVAS, AvasPlayer.SILENCE);
        Thread.sleep(ms);
    }
}

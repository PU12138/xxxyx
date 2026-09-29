package com.xxxyx.cultivation.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;

import com.xxxyx.cultivation.R;

/**
 * 背景音乐（MediaPlayer 循环）+ 音效（SoundPool）的统一管理。
 * 资源来自 res/raw 下的程序化生成 WAV。
 */
public class GameAudio {
    private final Context ctx;
    private SoundPool sfx;
    private MediaPlayer bgm;
    private int sSwing, sHit, sDeath, sBreak, sPickup, sHurt, sSkill;
    private boolean sfxLoaded = false;
    private boolean muted = false;
    private boolean bgmPlaying = false;

    public GameAudio(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        SoundPool.Builder b = new SoundPool.Builder();
        b.setMaxStreams(8);
        b.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        sfx = b.build();
        sfx.setOnLoadCompleteListener((sp, sampleId, status) -> sfxLoaded = true);
        sSwing = sfx.load(ctx, R.raw.sfx_swing, 1);
        sHit = sfx.load(ctx, R.raw.sfx_hit, 1);
        sDeath = sfx.load(ctx, R.raw.sfx_death, 1);
        sBreak = sfx.load(ctx, R.raw.sfx_break, 1);
        sPickup = sfx.load(ctx, R.raw.sfx_pickup, 1);
        sHurt = sfx.load(ctx, R.raw.sfx_hurt, 1);
        sSkill = sfx.load(ctx, R.raw.sfx_skill, 1);
        bgm = MediaPlayer.create(ctx, R.raw.bgm);
        if (bgm != null) {
            bgm.setLooping(true);
            bgm.setVolume(0.5f, 0.5f);
        }
    }

    public void startBgm() {
        if (muted || bgm == null || bgmPlaying) return;
        try {
            bgm.start();
            bgmPlaying = true;
        } catch (Exception ignored) {
        }
    }

    public void pauseBgm() {
        if (bgm != null && bgmPlaying) {
            try { bgm.pause(); } catch (Exception ignored) {
            }
            bgmPlaying = false;
        }
    }

    public void resumeBgm() {
        if (muted || bgm == null || bgmPlaying) return;
        try { bgm.start(); bgmPlaying = true; } catch (Exception ignored) {
        }
    }

    public void stopBgm() {
        if (bgm != null) {
            try { bgm.stop(); } catch (Exception ignored) {
            }
            bgmPlaying = false;
        }
    }

    public void release() {
        if (bgm != null) {
            try { bgm.release(); } catch (Exception ignored) {
            }
            bgm = null;
        }
        if (sfx != null) {
            sfx.release();
            sfx = null;
        }
    }

    public void setMuted(boolean m) {
        this.muted = m;
        if (m) pauseBgm();
        else resumeBgm();
    }

    public boolean isMuted() {
        return muted;
    }

    private void play(int id, float vol) {
        if (muted || !sfxLoaded || sfx == null) return;
        try { sfx.play(id, vol, vol, 1, 0, 1f); } catch (Exception ignored) {
        }
    }

    public void swing() { play(sSwing, 0.5f); }
    public void hit() { play(sHit, 0.7f); }
    public void death() { play(sDeath, 0.7f); }
    public void breakthrough() { play(sBreak, 0.9f); }
    public void pickup() { play(sPickup, 0.6f); }
    public void hurt() { play(sHurt, 0.7f); }
    public void skill() { play(sSkill, 0.7f); }
}

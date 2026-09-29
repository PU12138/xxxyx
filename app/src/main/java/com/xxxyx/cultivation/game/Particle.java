package com.xxxyx.cultivation.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** 通用粒子，用于命中、突破、死亡等视觉特效。 */
public class Particle extends Entity {
    private final float vx, vy;
    private float life;
    private final float maxLife;
    private final int color;
    private final float size;
    private final boolean gravity;

    public Particle(float x, float y, float vx, float vy, float life, int color, float size, boolean gravity) {
        super(x, y, size);
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.color = color;
        this.size = size;
        this.gravity = gravity;
    }

    @Override
    public void update(float dt, GameWorld world) {
        x += vx * dt;
        y += vy * dt;
        if (gravity) {
            // 模拟轻微下落
            y += 60f * dt;
        }
        life -= dt;
        if (life <= 0) alive = false;
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        float a = Math.max(0f, life / maxLife);
        int alpha = (int) (255 * a);
        int c = (alpha << 24) | (color & 0x00FFFFFF);
        paint.setColor(c);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, size * (0.4f + 0.6f * a), paint);
    }

    /** 在区域内随机喷射若干粒子（爆炸感） */
    public static void burst(GameWorld w, float x, float y, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            double ang = Math.random() * Math.PI * 2;
            float sp = speed * (0.4f + (float) Math.random() * 0.6f);
            float vx = (float) Math.cos(ang) * sp;
            float vy = (float) Math.sin(ang) * sp;
            float life = 0.3f + (float) Math.random() * 0.5f;
            float size = 3f + (float) Math.random() * 4f;
            w.addParticle(new Particle(x, y, vx, vy, life, color, size, false));
        }
    }

    /** 向上飘升的灵气粒子（突破特效） */
    public static void rise(GameWorld w, float x, float y, int color, int count) {
        for (int i = 0; i < count; i++) {
            float vx = (float) (Math.random() - 0.5) * 40f;
            float vy = -120f - (float) Math.random() * 160f;
            float life = 0.8f + (float) Math.random() * 0.6f;
            float size = 4f + (float) Math.random() * 5f;
            w.addParticle(new Particle(x + (float) (Math.random() - 0.5) * 40, y, vx, vy, life, color, size, false));
        }
    }

    /** 伤害飘字不在此处，由 GameWorld 用 TextParticle 处理；这里仅纯图形粒子。 */
    public static int colorFromRarity(int rarityColor) {
        return Color.argb(255, Color.red(rarityColor), Color.green(rarityColor), Color.blue(rarityColor));
    }
}

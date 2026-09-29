package com.xxxyx.cultivation.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * 弹幕/剑气/法术飞行物。
 * fromPlayer=true 时伤害作用于怪物，否则作用于玩家。
 * 碰撞由 GameWorld 统一解析，本类只负责运动与绘制。
 */
public class Projectile extends Entity {
    public final float vx, vy;
    public final float damage;
    public final boolean fromPlayer;
    public final boolean isAoE;       // 是否范围伤害（命中后对周围造成伤害）
    public final float aoeRadius;
    public final int color;
    public float life;
    public final float maxLife;
    public int pierce = 0;            // 剩余可穿透目标数
    public boolean crit = false;
    private float trailX, trailY;
    private boolean trailInit = false;

    public Projectile(float x, float y, float vx, float vy, float radius, float damage,
                      boolean fromPlayer, int color, float life, int pierce, boolean isAoE, float aoeRadius) {
        super(x, y, radius);
        this.vx = vx;
        this.vy = vy;
        this.damage = damage;
        this.fromPlayer = fromPlayer;
        this.color = color;
        this.life = life;
        this.maxLife = life;
        this.pierce = pierce;
        this.isAoE = isAoE;
        this.aoeRadius = aoeRadius;
        this.trailX = x;
        this.trailY = y;
    }

    @Override
    public void update(float dt, GameWorld world) {
        trailX = x;
        trailY = y;
        x += vx * dt;
        y += vy * dt;
        life -= dt;
        if (life <= 0) alive = false;
        // 出屏判定
        if (x < -50 || x > world.width + 50 || y < world.gameTop - 80 || y > world.height + 80) {
            alive = false;
        }
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        // 拖尾
        if (trailInit) {
            paint.setColor((fromPlayer ? Color.argb(160, 255, 230, 120) : Color.argb(160, 255, 80, 80)));
            paint.setStrokeWidth(radius * 0.8f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(trailX, trailY, x, y, paint);
        }
        trailInit = true;
        // 弹体：径向渐变发光
        int core = color;
        int edge = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color));
        paint.setShader(new RadialGradient(x, y, radius * 2.4f, core, edge, Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, radius * 2.4f, paint);
        paint.setShader(null);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, radius * 0.9f, paint);
        if (crit) {
            paint.setColor(Color.YELLOW);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            canvas.drawCircle(x, y, radius * 3f, paint);
        }
    }
}

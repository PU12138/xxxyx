package com.xxxyx.cultivation.game;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * 游戏中所有可绘制、可更新实体的基类。
 * 坐标系为屏幕像素，(x,y) 为实体中心。
 */
public abstract class Entity {
    public float x, y;
    public float radius;
    public boolean alive = true;

    public Entity(float x, float y, float radius) {
        this.x = x;
        this.y = y;
        this.radius = radius;
    }

    /** 每帧更新逻辑，dt 单位秒 */
    public abstract void update(float dt, GameWorld world);

    /** 每帧绘制 */
    public abstract void draw(Canvas canvas, Paint paint);

    /** 圆形碰撞检测 */
    public boolean collides(Entity other) {
        float dx = x - other.x;
        float dy = y - other.y;
        float r = radius + other.radius;
        return dx * dx + dy * dy <= r * r;
    }

    /** 距离平方 */
    public float distSq(Entity other) {
        float dx = x - other.x;
        float dy = y - other.y;
        return dx * dx + dy * dy;
    }
}

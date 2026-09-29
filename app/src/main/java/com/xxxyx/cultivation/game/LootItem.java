package com.xxxyx.cultivation.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * 掉落物：法器（武器/护甲/灵玉）。
 * 拾取后若属性优于当前装备则自动装备，否则转化为灵石。
 */
public class LootItem extends Entity {
    public enum Rarity {
        COMMON("凡器", Color.rgb(0xC8, 0xC8, 0xD0), 1.0f),
        FINE("灵器", Color.rgb(0x6F, 0xE0, 0x9E), 1.5f),
        RARE("宝器", Color.rgb(0x6E, 0xB5, 0xFF), 2.1f),
        EPIC("仙器", Color.rgb(0xC9, 0x8A, 0xFF), 3.0f),
        LEGENDARY("神器", Color.rgb(0xFF, 0xD1, 0x4F), 4.5f);

        public final String label;
        public final int color;
        public final float mult;
        Rarity(String l, int c, float m) { label = l; color = c; mult = m; }
    }

    public enum Slot {
        WEAPON("法剑", "剑"), ARMOR("法袍", "袍"), ACCESSORY("灵玉", "玉");
        public final String name;
        public final String glyph;
        Slot(String n, String g) { name = n; glyph = g; }
    }

    public final Slot slot;
    public final Rarity rarity;
    public final int atk, def, hp, mp;
    public final String displayName;
    private float vx, vy;
    private boolean settled = false;
    private float settleT = 0f;
    private float bob;

    public LootItem(Slot slot, Rarity rarity, float x, float y, int playerLayer) {
        super(x, y, 14f);
        this.slot = slot;
        this.rarity = rarity;
        // 属性随玩家进度与稀有度增长
        float base = (10 + playerLayer * 4) * rarity.mult;
        switch (slot) {
            case WEAPON:
                this.atk = Math.round(base * 1.6f);
                this.def = Math.round(base * 0.2f);
                this.hp = 0;
                this.mp = Math.round(base * 0.4f);
                break;
            case ARMOR:
                this.atk = 0;
                this.def = Math.round(base * 1.4f);
                this.hp = Math.round(base * 6f);
                this.mp = 0;
                break;
            default: // ACCESSORY
                this.atk = Math.round(base * 0.5f);
                this.def = Math.round(base * 0.5f);
                this.hp = Math.round(base * 2f);
                this.mp = Math.round(base * 3f);
                break;
        }
        this.displayName = rarity.label + "·" + slot.name;
        this.vx = (float) (Math.random() - 0.5) * 140f;
        this.vy = -120f - (float) Math.random() * 60f;
    }

    @Override
    public void update(float dt, GameWorld world) {
        if (!settled) {
            x += vx * dt;
            y += vy * dt;
            vy += 600f * dt; // 重力
            settleT += dt;
            // 落地或时间到则稳定
            if (y >= world.height - 80f || settleT > 0.8f) {
                settled = true;
                vy = 0;
                vx = 0;
            }
            // 边界
            if (x < 20) x = 20;
            if (x > world.width - 20) x = world.width - 20;
        } else {
            bob += dt * 3f;
        }
        // 磁吸：玩家进入范围则被吸引
        Player p = world.player;
        float dx = p.x - x;
        float dy = p.y - y;
        float d2 = dx * dx + dy * dy;
        float magnet = 160f * (1f + p.magnetLevel * 0.4f);
        if (d2 < magnet * magnet) {
            float d = (float) Math.sqrt(d2) + 0.001f;
            float pull = 520f;
            x += dx / d * pull * dt;
            y += dy / d * pull * dt;
        }
        // 拾取
        if (d2 < (p.radius + radius) * (p.radius + radius)) {
            p.acquireItem(this);
            alive = false;
        }
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        float yy = y + (settled ? (float) Math.sin(bob) * 3f : 0f);
        // 光晕
        paint.setColor(Color.argb(80, Color.red(rarity.color), Color.green(rarity.color), Color.blue(rarity.color)));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, yy, radius * 1.8f, paint);
        // 主体
        paint.setColor(rarity.color);
        canvas.drawCircle(x, yy, radius, paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawCircle(x, yy, radius, paint);
        // 字
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(16f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(slot.glyph, x, yy + 5f, paint);
    }
}

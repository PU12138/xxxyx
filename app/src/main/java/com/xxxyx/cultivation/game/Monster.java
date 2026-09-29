package com.xxxyx.cultivation.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * 妖兽：从屏幕上方/侧方刷新，向玩家逼近或远程攻击。
 * 属性随波次与玩家境界递增。
 */
public class Monster extends Entity {
    public enum Type {
        BOAR("妖猪", Color.rgb(0x9C, 0x6B, 0x4E), 26f, false, 1.0f),
        SNAKE("灵蛇", Color.rgb(0x4E, 0xC0, 0x6B), 18f, false, 1.15f),
        BAT("血蝠", Color.rgb(0xB0, 0x3A, 0x6E), 20f, true, 1.05f),
        CULTIVATOR("邪修", Color.rgb(0x7A, 0x4E, 0xC0), 24f, false, 1.6f),
        DEMONKING("渡劫妖王", Color.rgb(0xE2, 0x4B, 0x2A), 46f, false, 4.0f);

        public final String name;
        public final int color;
        public final float baseRadius;
        public final boolean ranged;
        public final float tier; // 强度倍率
        Type(String n, int c, float r, boolean rg, float t) { name = n; color = c; baseRadius = r; ranged = rg; tier = t; }
    }

    public final Type type;
    public float hp, maxHp;
    public float atk;
    public float speed;
    public int xpReward;
    public int lingshi;
    public final boolean ranged;
    private float attackCd = 0f;
    public float hurtFlash = 0f;
    private float bob = 0f;

    public Monster(Type type, float x, float y, int wave, int playerLayer) {
        super(x, y, type.baseRadius);
        this.type = type;
        this.ranged = type.ranged;
        // 缩放：波次 + 玩家进度 + 类型倍率
        float waveScale = 1f + wave * 0.18f;
        float layerScale = 1f + playerLayer * 0.12f;
        float s = type.tier * waveScale * layerScale;
        this.maxHp = Math.round(28f * s);
        this.hp = maxHp;
        this.atk = Math.round(6f * s);
        this.speed = (type == Type.DEMONKING ? 38f : 60f + (float) Math.random() * 30f) / type.tier;
        this.xpReward = Math.max(8, Math.round(14f * s));
        this.lingshi = Math.max(1, Math.round(3f * s));
    }

    @Override
    public void update(float dt, GameWorld world) {
        Player p = world.player;
        float dx = p.x - x;
        float dy = p.y - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
        float nx = dx / d;
        float ny = dy / d;

        if (ranged) {
            // 远程：保持距离 ~ 280
            float desired = 280f;
            if (d > desired + 30) {
                x += nx * speed * dt;
                y += ny * speed * dt;
            } else if (d < desired - 30) {
                x -= nx * speed * dt;
                y -= ny * speed * dt;
            }
            // 侧向漂移
            x += -ny * speed * 0.4f * dt;
            attackCd -= dt;
            if (attackCd <= 0 && d < 520) {
                attackCd = 1.8f;
                world.spawnEnemyProjectile(x, y, p.x, p.y, atk);
            }
        } else {
            // 近战：直冲玩家
            x += nx * speed * dt;
            y += ny * speed * dt;
            attackCd -= dt;
            if (d < radius + p.radius + 4 && attackCd <= 0) {
                attackCd = 1.0f;
                p.takeDamage(atk * 0.6f, world);
            }
        }

        if (hurtFlash > 0) hurtFlash -= dt;
        bob += dt * (type == Type.DEMONKING ? 2f : 4f);
    }

    public void takeDamage(float amount, GameWorld world) {
        // 暴击在 GameWorld 计算后传入；此处仅扣血
        hp -= amount;
        hurtFlash = 0.12f;
        world.addDamageText(x, y - radius, Math.round(amount));
        if (hp <= 0) {
            alive = false;
            world.onMonsterDeath(this);
        }
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        float yy = y + (float) Math.sin(bob) * 2f;
        // 受击闪白
        int body = type.color;
        if (hurtFlash > 0) {
            body = Color.argb(255, 255, 255, 220);
        }
        // 阴影
        paint.setColor(Color.argb(60, 0, 0, 0));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawOval(x - radius, yy + radius * 0.8f, x + radius, yy + radius * 1.2f, paint);

        // 身体
        paint.setColor(body);
        canvas.drawCircle(x, yy, radius, paint);
        // 眼睛
        float er = radius * 0.18f;
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x - radius * 0.35f, yy - radius * 0.2f, er, paint);
        canvas.drawCircle(x + radius * 0.35f, yy - radius * 0.2f, er, paint);
        paint.setColor(Color.BLACK);
        canvas.drawCircle(x - radius * 0.35f, yy - radius * 0.2f, er * 0.5f, paint);
        canvas.drawCircle(x + radius * 0.35f, yy - radius * 0.2f, er * 0.5f, paint);

        // Boss 头顶角
        if (type == Type.DEMONKING) {
            paint.setColor(Color.rgb(0x55, 0x11, 0x11));
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(x - radius * 0.5f, yy - radius * 0.7f);
            path.lineTo(x - radius * 0.2f, yy - radius * 1.4f);
            path.lineTo(x, yy - radius * 0.7f);
            path.close();
            canvas.drawPath(path, paint);
            android.graphics.Path path2 = new android.graphics.Path();
            path2.moveTo(x, yy - radius * 0.7f);
            path2.lineTo(x + radius * 0.2f, yy - radius * 1.4f);
            path2.lineTo(x + radius * 0.5f, yy - radius * 0.7f);
            path2.close();
            canvas.drawPath(path2, paint);
        }

        // 血条
        float bw = radius * 2f;
        float bh = 5f;
        float by = yy - radius - 12f;
        paint.setColor(Color.argb(180, 0, 0, 0));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(x - bw / 2, by, x + bw / 2, by + bh, paint);
        float ratio = Math.max(0, hp / maxHp);
        paint.setColor(Color.rgb(0xD2, 0x3B, 0x3B));
        canvas.drawRect(x - bw / 2, by, x - bw / 2 + bw * ratio, by + bh, paint);

        // 名字
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(13f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(type.name, x, by - 4f, paint);
    }
}

package com.xxxyx.cultivation.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

/**
 * 玩家：修仙者。包含境界、属性、装备、技能、自动攻击与移动。
 */
public class Player extends Entity {
    // 境界与进度
    public int realmIndex = 0;          // 0..8
    public int layer = 1;                // 1..9 当前境界内的重数
    public float xp = 0f;
    public float xpNeeded = 78f;

    // 属性
    public float maxHp, maxMp;
    public float hp, mp;
    public float atk, def;
    public float critChance = 0.05f;
    public float critMult = 2.0f;
    public float moveSpeed = 260f;

    // 装备
    public LootItem weapon, armor, accessory;
    public int magnetLevel = 0;

    // 资源
    public int lingshi = 0;
    public int killCount = 0;
    public int pillCount = 3;

    // 冷却
    public float attackCd = 0f;
    public final float skill1Max = 5f, skill2Max = 12f;
    public float skill1Cd = 0f, skill2Cd = 0f;
    public float pillCd = 0f;
    private final float pillCdMax = 1.0f;

    // 状态
    public float shieldTimer = 0f;
    public float shieldAmount = 0f;
    public float invulnTimer = 0f;
    public float hurtFlash = 0f;
    public float breakthroughFlash = 0f;
    public float facingX = 0f, facingY = -1f; // 朝向（绘制用）
    public boolean dead = false;

    public Player(float x, float y) {
        super(x, y, 22f);
        recalcStats();
        hp = maxHp;
        mp = maxMp;
    }

    public CultivationRealm realm() {
        return CultivationRealm.byIndex(realmIndex);
    }

    public int totalLayer() {
        return realmIndex * 9 + layer;
    }

    /** 根据境界/重数/装备重算属性 */
    public void recalcStats() {
        CultivationRealm r = realm();
        int tl = totalLayer();
        float m = r.statMult;
        float baseMaxHp = (100 + tl * 16) * m;
        float baseMaxMp = (50 + tl * 9) * m;
        float baseAtk = (12 + tl * 2.5f) * m;
        float baseDef = (5 + tl * 1.8f) * m;
        // 装备加成
        float eHp = 0, eMp = 0, eAtk = 0, eDef = 0;
        if (weapon != null) { eAtk += weapon.atk; eDef += weapon.def; eMp += weapon.mp; }
        if (armor != null) { eDef += armor.def; eHp += armor.hp; }
        if (accessory != null) { eAtk += accessory.atk; eDef += accessory.def; eHp += accessory.hp; eMp += accessory.mp; }
        float oldMaxHp = maxHp, oldMaxMp = maxMp;
        maxHp = baseMaxHp + eHp;
        maxMp = baseMaxMp + eMp;
        atk = baseAtk + eAtk;
        def = baseDef + eDef;
        critChance = Math.min(0.5f, 0.05f + tl * 0.004f);
        // 防止溢出
        if (hp > maxHp) hp = maxHp;
        if (mp > maxMp) mp = maxMp;
        // 突破时回满
    }

    public void update(float dt, GameWorld w, float joyX, float joyY, float joyMag) {
        if (dead) return;
        // 冷却递减
        attackCd -= dt;
        skill1Cd -= dt;
        skill2Cd -= dt;
        pillCd -= dt;
        if (shieldTimer > 0) {
            shieldTimer -= dt;
            if (shieldTimer <= 0) shieldAmount = 0;
        }
        if (invulnTimer > 0) invulnTimer -= dt;
        if (hurtFlash > 0) hurtFlash -= dt;
        if (breakthroughFlash > 0) breakthroughFlash -= dt;

        // 移动（摇杆）
        if (joyMag > 0.05f) {
            float nx = joyX, ny = joyY;
            if (joyMag > 1f) { nx /= joyMag; ny /= joyMag; }
            x += nx * moveSpeed * dt;
            y += ny * moveSpeed * dt;
            facingX = nx;
            facingY = ny;
        }
        // 边界
        float pad = radius + 4f;
        if (x < pad) x = pad;
        if (x > w.width - pad) x = w.width - pad;
        if (y < w.gameTop + pad) y = w.gameTop + pad;
        if (y > w.height - w.bottomBar - pad) y = w.height - w.bottomBar - pad;

        // 灵气自然恢复（HP 缓慢，MP 较快）
        float ooc = nearestMonsterDist(w);
        boolean inCombat = nearestMonsterDist(w) < 260f;
        if (!inCombat) hp += maxHp * 0.04f * dt;
        mp += maxMp * 0.12f * dt;
        if (hp > maxHp) hp = maxHp;
        if (mp > maxMp) mp = maxMp;

        // 自动攻击最近目标
        if (attackCd <= 0) {
            Monster t = nearestMonster(w);
            if (t != null) {
                attackCd = Math.max(0.18f, 0.55f - totalLayer() * 0.004f);
                fireSwordQi(w, t);
            }
        }
    }

    private float nearestMonsterDist(GameWorld w) {
        Monster t = nearestMonster(w);
        return t == null ? Float.MAX_VALUE : (float) Math.sqrt(distSq(t));
    }

    public Monster nearestMonster(GameWorld w) {
        Monster best = null;
        float bd = Float.MAX_VALUE;
        for (Monster m : w.monsters) {
            if (!m.alive) continue;
            float d = distSq(m);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    private void fireSwordQi(GameWorld w, Monster target) {
        float dx = target.x - x;
        float dy = target.y - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
        float sp = 620f;
        boolean crit = Math.random() < critChance;
        float dmg = atk * (0.9f + (float) Math.random() * 0.2f);
        if (crit) dmg *= critMult;
        Projectile p = new Projectile(x, y, dx / d * sp, dy / d * sp, 8f, dmg, true,
                Color.rgb(255, 235, 150), 1.4f, crit ? 0 : 0, false, 0);
        p.crit = crit;
        w.spawnPlayerProjectile(p);
        w.audio.swing();
    }

    /** 万剑诀：周围范围伤害 + 环形剑气 */
    public void castSkill1(GameWorld w) {
        if (skill1Cd > 0 || mp < 30) return;
        skill1Cd = skill1Max;
        mp -= 30;
        w.audio.skill();
        float R = 230f;
        // 即时范围伤害
        for (Monster m : w.monsters) {
            if (!m.alive) continue;
            if (distSq(m) <= R * R) {
                boolean crit = Math.random() < critChance;
                float dmg = atk * 1.6f * (crit ? critMult : 1f);
                m.takeDamage(dmg, w);
            }
        }
        // 环形 12 道剑气
        for (int i = 0; i < 12; i++) {
            double ang = (Math.PI * 2 * i) / 12;
            float vx = (float) Math.cos(ang) * 540f;
            float vy = (float) Math.sin(ang) * 540f;
            boolean crit = Math.random() < critChance;
            float dmg = atk * 1.1f * (crit ? critMult : 1f);
            Projectile p = new Projectile(x, y, vx, vy, 10f, dmg, true,
                    Color.rgb(180, 220, 255), 0.7f, 2, false, 0);
            p.crit = crit;
            w.spawnPlayerProjectile(p);
        }
        Particle.burst(w, x, y, Color.rgb(180, 220, 255), 24, 260f);
    }

    /** 金刚护体：回血 + 护盾 */
    public void castSkill2(GameWorld w) {
        if (skill2Cd > 0 || mp < 40) return;
        skill2Cd = skill2Max;
        mp -= 40;
        w.audio.skill();
        hp = Math.min(maxHp, hp + maxHp * 0.3f);
        shieldAmount = maxHp * 0.2f;
        shieldTimer = 5f;
        invulnTimer = Math.max(invulnTimer, 0.4f);
        Particle.rise(w, x, y, Color.rgb(120, 200, 255), 26);
    }

    /** 灵丹：回血 */
    public void usePill(GameWorld w) {
        if (pillCd > 0 || pillCount <= 0 || dead) return;
        pillCd = pillCdMax;
        pillCount--;
        hp = Math.min(maxHp, hp + maxHp * 0.45f);
        w.audio.pickup();
        Particle.rise(w, x, y, Color.rgb(120, 255, 160), 16);
    }

    public void takeDamage(float amount, GameWorld w) {
        if (dead) return;
        if (invulnTimer > 0) return;
        float dmg = Math.max(1f, amount - def * 0.5f);
        if (shieldTimer > 0 && shieldAmount > 0) {
            if (shieldAmount >= dmg) {
                shieldAmount -= dmg;
                dmg = 0;
            } else {
                dmg -= shieldAmount;
                shieldAmount = 0;
            }
        }
        if (dmg > 0) {
            hp -= dmg;
            hurtFlash = 0.12f;
            invulnTimer = 0.25f;
            w.audio.hurt();
            Particle.burst(w, x, y, Color.rgb(220, 60, 60), 6, 120f);
            if (hp <= 0) {
                hp = 0;
                dead = true;
                w.onPlayerDeath();
            }
        }
    }

    public void gainXp(float amount, GameWorld w) {
        if (dead) return;
        xp += amount;
        while (xp >= xpNeeded) {
            xp -= xpNeeded;
            advanceLayer(w);
        }
    }

    private void advanceLayer(GameWorld w) {
        boolean maxRealm = realmIndex >= CultivationRealm.count() - 1;
        if (layer < 9) {
            layer++;
        } else if (!maxRealm) {
            realmIndex++;
            layer = 1;
        } else {
            // 已达极限，转化为灵石
            lingshi += 100;
            return;
        }
        breakthrough(w);
    }

    private void breakthrough(GameWorld w) {
        recalcStats();
        hp = maxHp;
        mp = maxMp;
        pillCount += 1;
        breakthroughFlash = 1.2f;
        invulnTimer = Math.max(invulnTimer, 1.2f);
        w.audio.breakthrough();
        Particle.rise(w, x, y, realm().auraColor, 60);
        Particle.burst(w, x, y, realm().auraColor, 40, 320f);
        w.onBreakthrough();
        xpNeeded = 50f + totalLayer() * 28f;
    }

    /** 拾取装备：优于当前则装备，否则变灵石 */
    public void acquireItem(LootItem item) {
        LootItem current = null;
        switch (item.slot) {
            case WEAPON: current = weapon; break;
            case ARMOR: current = armor; break;
            case ACCESSORY: current = accessory; break;
        }
        boolean better = isBetter(item, current);
        if (better) {
            switch (item.slot) {
                case WEAPON: weapon = item; break;
                case ARMOR: armor = item; break;
                case ACCESSORY: accessory = item; break;
            }
            recalcStats();
        } else {
            lingshi += Math.max(5, item.rarity.ordinal() * 10 + 5);
        }
    }

    private boolean isBetter(LootItem candidate, LootItem current) {
        if (current == null) return true;
        int cs = candidate.atk * 2 + candidate.def * 2 + candidate.hp / 5 + candidate.mp / 5;
        int cur = current.atk * 2 + current.def * 2 + current.hp / 5 + current.mp / 5;
        return cs > cur;
    }

    @Override
    public void update(float dt, GameWorld world) {
        // 由 GameWorld 调用带摇杆参数的版本
    }

    @Override
    public void draw(Canvas canvas, Paint paint) {
        if (dead) return;
        CultivationRealm r = realm();
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() * 0.005);

        // 光环
        paint.setColor(Color.argb((int) (60 + 40 * pulse), Color.red(r.auraColor), Color.green(r.auraColor), Color.blue(r.auraColor)));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, radius * 2.4f, paint);

        // 护盾
        if (shieldTimer > 0) {
            paint.setColor(Color.argb(120, 120, 200, 255));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawCircle(x, y, radius * 1.8f, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // 阴影
        paint.setColor(Color.argb(70, 0, 0, 0));
        canvas.drawOval(x - radius, y + radius * 0.8f, x + radius, y + radius * 1.2f, paint);

        // 法袍（梯形）
        Path robe = new Path();
        robe.moveTo(x - radius * 0.45f, y - radius * 0.2f);
        robe.lineTo(x + radius * 0.45f, y - radius * 0.2f);
        robe.lineTo(x + radius * 0.95f, y + radius * 1.1f);
        robe.lineTo(x - radius * 0.95f, y + radius * 1.1f);
        robe.close();
        int robeColor = hurtFlash > 0 ? Color.WHITE : r.auraColor;
        paint.setColor(robeColor);
        canvas.drawPath(robe, paint);
        paint.setColor(Color.argb(120, 255, 255, 255));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawPath(robe, paint);

        // 头
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(hurtFlash > 0 ? Color.WHITE : Color.rgb(0xF5, 0xD8, 0xB8));
        canvas.drawCircle(x, y - radius * 0.5f, radius * 0.42f, paint);
        // 发髻
        paint.setColor(Color.rgb(0x33, 0x22, 0x11));
        canvas.drawCircle(x, y - radius * 0.8f, radius * 0.22f, paint);

        // 突破闪光环
        if (breakthroughFlash > 0) {
            float t = 1f - breakthroughFlash / 1.2f;
            paint.setColor(Color.argb((int) (200 * (1 - t)), 255, 255, 220));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6f);
            canvas.drawCircle(x, y, radius * (1f + t * 4f), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        // 名字 + 境界
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(13f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(r.name + layer + "重", x, y - radius * 2.2f, paint);
    }
}

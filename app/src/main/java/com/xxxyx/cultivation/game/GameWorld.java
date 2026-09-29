package com.xxxyx.cultivation.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;

import com.xxxyx.cultivation.audio.GameAudio;
import com.xxxyx.cultivation.ui.ActionButton;
import com.xxxyx.cultivation.ui.Joystick;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 游戏世界：持有玩家、怪物、彈幕、掉落、粒子；负责刷新/碰撞/HUD/输入分发。
 * 由 GameView 的渲染线程驱动 update/draw。
 */
public class GameWorld {
    public enum State { MENU, PLAYING, PAUSED, GAMEOVER }

    // 屏幕
    public int width = 1080, height = 1920;
    public float density = 3f;
    public float gameTop = 90f;     // HUD 高度
    public float bottomBar = 220f; // 底部控制区高度

    // 实体集合
    public Player player;
    public final List<Monster> monsters = new ArrayList<>();
    private final List<Projectile> playerProj = new ArrayList<>();
    private final List<Projectile> enemyProj = new ArrayList<>();
    private final List<LootItem> loot = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<DamageText> texts = new ArrayList<>();

    // 输入
    public final Joystick joystick;
    public final ActionButton btnSkill1;
    public final ActionButton btnSkill2;
    public final ActionButton btnPill;
    public final ActionButton btnMute;

    // 音频
    public final GameAudio audio;

    // 状态
    public State state = State.MENU;
    public int wave = 0;
    private int toSpawn = 0;
    private float spawnTimer = 0f;
    private float waveBreakTimer = 0f;
    private boolean waveActive = false;
    private float menuTime = 0f;
    private float gameoverTime = 0f;
    private int bestWave = 0;

    // 视觉
    private float shake = 0f;
    private float cloudOffset = 0f;
    private final Random rng = new Random();

    // 资源偏好
    private final android.content.SharedPreferences prefs;

    public GameWorld(Context ctx, GameAudio audio) {
        this.audio = audio;
        this.prefs = ctx.getApplicationContext().getSharedPreferences("xiantu", Context.MODE_PRIVATE);
        this.bestWave = prefs.getInt("best_wave", 0);
        float dp = ctx.getResources().getDisplayMetrics().density;
        this.density = dp;
        this.gameTop = 86f * dp;
        this.bottomBar = 200f * dp;
        joystick = new Joystick(0, 0, 70f * dp, 32f * dp);
        btnSkill1 = new ActionButton(0, 0, 46f * dp, "剑诀", Color.rgb(0x6E, 0xB5, 0xFF));
        btnSkill2 = new ActionButton(0, 0, 46f * dp, "护体", Color.rgb(0x6F, 0xE0, 0x9E));
        btnPill = new ActionButton(0, 0, 36f * dp, "丹", Color.rgb(0xE8, 0xC2, 0x6A));
        btnMute = new ActionButton(0, 0, 26f * dp, "♪", Color.rgb(0xC8, 0xC8, 0xD0));
        layoutControls();
        resetPlayer();
    }

    private void resetPlayer() {
        player = new Player(width / 2f, height - bottomBar - 60f * density);
    }

    /** 根据屏幕尺寸布置控件位置 */
    private void layoutControls() {
        float dp = density;
        joystick.layout(110f * dp, height - 110f * dp, 70f * dp, 32f * dp);
        btnSkill1.layout(width - 80f * dp, height - 130f * dp, 46f * dp);
        btnSkill2.layout(width - 170f * dp, height - 80f * dp, 46f * dp);
        btnPill.layout(width - 80f * dp, height - 50f * dp, 36f * dp);
        btnMute.layout(width - 36f * dp, 36f * dp, 26f * dp);
    }

    public void resize(int w, int h) {
        this.width = w;
        this.height = h;
        layoutControls();
        if (player != null) {
            if (player.x > w) player.x = w / 2f;
            if (player.y > h - bottomBar) player.y = h - bottomBar - 60f * density;
        }
    }

    // ---------- 生命周期 ----------
    public void start() {
        restart();
    }

    public void restart() {
        monsters.clear();
        playerProj.clear();
        enemyProj.clear();
        loot.clear();
        particles.clear();
        texts.clear();
        wave = 0;
        toSpawn = 0;
        waveActive = false;
        waveBreakTimer = 2f;
        shake = 0f;
        resetPlayer();
        state = State.PLAYING;
        audio.startBgm();
    }

    public void pause() {
        if (state == State.PLAYING) state = State.PAUSED;
        audio.pauseBgm();
    }

    public void resume() {
        if (state == State.PAUSED) state = State.PLAYING;
        audio.resumeBgm();
    }

    // ---------- 生成 ----------
    private void startWave() {
        wave++;
        boolean bossWave = (wave % 5 == 0);
        if (bossWave) {
            toSpawn = 1 + wave / 5; // boss 数量
        } else {
            toSpawn = 4 + wave * 2;
            if (toSpawn > 18) toSpawn = 18;
        }
        spawnTimer = 0f;
        waveActive = true;
    }

    private Monster.Type pickType() {
        if (wave % 5 == 0) return Monster.Type.DEMONKING;
        int r = rng.nextInt(100);
        if (wave >= 3 && r < 12) return Monster.Type.CULTIVATOR;
        if (r < 35) return Monster.Type.SNAKE;
        if (r < 55) return Monster.Type.BAT;
        return Monster.Type.BOAR;
    }

    private void spawnOne() {
        Monster.Type t = pickType();
        float margin = 60f;
        float sx = margin + rng.nextFloat() * (width - margin * 2);
        float sy = gameTop - 40f - rng.nextFloat() * 40f;
        if (t == Monster.Type.DEMONKING) {
            sx = width / 2f + (rng.nextFloat() - 0.5f) * width * 0.3f;
            sy = gameTop - 60f;
        }
        Monster m = new Monster(t, sx, sy, wave, player.totalLayer());
        monsters.add(m);
    }

    // ---------- 实体生成接口（供 Player/Monster 调用） ----------
    public void spawnPlayerProjectile(Projectile p) {
        playerProj.add(p);
    }

    public void spawnEnemyProjectile(float fx, float fy, float tx, float ty, float dmg) {
        float dx = tx - fx, dy = ty - fy;
        float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
        float sp = 360f;
        Projectile p = new Projectile(fx, fy, dx / d * sp, dy / d * sp, 9f, dmg, false,
                Color.rgb(255, 90, 110), 3f, 0, false, 0);
        enemyProj.add(p);
    }

    public void addParticle(Particle p) {
        particles.add(p);
    }

    public void addDamageText(float x, float y, int amount) {
        texts.add(new DamageText(x, y, amount));
    }

    public void onMonsterDeath(Monster m) {
        audio.death();
        Particle.burst(this, m.x, m.y, m.type.color, m.type == Monster.Type.DEMONKING ? 50 : 16, 240f);
        player.killCount++;
        player.lingshi += m.lingshi;
        player.gainXp(m.xpReward, this);
        // 掉落
        float dropChance = 0.22f + (m.type == Monster.Type.DEMONKING ? 0.6f : 0f);
        if (rng.nextFloat() < dropChance) {
            LootItem.Rarity r = rollRarity(m);
            LootItem.Slot s = LootItem.Slot.values()[rng.nextInt(3)];
            loot.add(new LootItem(s, r, m.x, m.y, player.totalLayer()));
        }
    }

    private LootItem.Rarity rollRarity(Monster m) {
        int bonus = (m.type == Monster.Type.DEMONKING ? 3 : 0) + wave / 4;
        int r = rng.nextInt(100) + bonus;
        if (r >= 95) return LootItem.Rarity.LEGENDARY;
        if (r >= 80) return LootItem.Rarity.EPIC;
        if (r >= 60) return LootItem.Rarity.RARE;
        if (r >= 35) return LootItem.Rarity.FINE;
        return LootItem.Rarity.COMMON;
    }

    public void onPlayerDeath() {
        state = State.GAMEOVER;
        gameoverTime = 0f;
        audio.death();
        Particle.burst(this, player.x, player.y, Color.rgb(255, 120, 120), 40, 280f);
        shake = 0.6f;
        if (wave > bestWave) {
            bestWave = wave;
            prefs.edit().putInt("best_wave", bestWave).apply();
        }
    }

    public void onBreakthrough() {
        shake = Math.max(shake, 0.35f);
    }

    // ---------- 主更新 ----------
    public void update(float dt) {
        cloudOffset += dt * 8f;
        if (shake > 0) shake = Math.max(0, shake - dt);
        if (state == State.MENU) {
            menuTime += dt;
            ambientParticles(dt);
            return;
        }
        if (state == State.GAMEOVER) {
            gameoverTime += dt;
            // 仍更新粒子
            updateList(particles, dt);
            updateTexts(dt);
            return;
        }
        if (state != State.PLAYING) return;

        // 波次管理
        if (!waveActive) {
            waveBreakTimer -= dt;
            if (waveBreakTimer <= 0) startWave();
        } else {
            if (toSpawn > 0) {
                spawnTimer -= dt;
                if (spawnTimer <= 0) {
                    spawnTimer = 0.7f - Math.min(0.5f, wave * 0.03f);
                    spawnOne();
                    toSpawn--;
                }
            } else if (monsters.isEmpty()) {
                waveActive = false;
                waveBreakTimer = 2.5f;
                player.lingshi += 5 + wave;
                addDamageText(width / 2f, gameTop + 60f, 0); // 触发清波提示由 HUD 读取 waveBreakTimer
            }
        }

        // 玩家
        player.update(dt, this, joystick.dirX(), joystick.dirY(), joystick.mag());

        // 技能按钮
        if (btnSkill1.consumeJustPressed()) player.castSkill1(this);
        if (btnSkill2.consumeJustPressed()) player.castSkill2(this);
        if (btnPill.consumeJustPressed()) player.usePill(this);
        if (btnMute.consumeJustPressed()) audio.setMuted(!audio.isMuted());

        // 冷却进度（用于按钮遮罩）
        btnSkill1.cooldownRatio = Math.max(0, player.skill1Cd / player.skill1Max);
        btnSkill2.cooldownRatio = Math.max(0, player.skill2Cd / player.skill2Max);
        btnPill.cooldownRatio = Math.max(0, player.pillCd / 1f);
        btnMute.label = audio.isMuted() ? "♪̸" : "♪";

        // 更新实体
        for (Monster m : monsters) m.update(dt, this);
        updateList(playerProj, dt);
        updateList(enemyProj, dt);
        updateList(loot, dt);
        updateList(particles, dt);
        updateTexts(dt);

        // 碰撞
        resolveCollisions();

        // 清理
        cleanup(monsters);
        cleanup(playerProj);
        cleanup(enemyProj);
        cleanup(loot);
        cleanup(particles);
        removeDeadTexts();
    }

    private void ambientParticles(float dt) {
        if (rng.nextFloat() < dt * 6f) {
            float x = rng.nextFloat() * width;
            addParticle(new Particle(x, height + 10, 0, -40f - rng.nextFloat() * 40f,
                    3f + rng.nextFloat() * 3f, Color.rgb(0x99, 0xCC, 0xFF), 3f, false));
        }
        updateList(particles, dt);
    }

    private <T extends Entity> void updateList(List<T> list, float dt) {
        for (int i = 0; i < list.size(); i++) list.get(i).update(dt, this);
    }

    private <T extends Entity> void cleanup(List<T> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (!list.get(i).alive) list.remove(i);
        }
    }

    private void removeDeadTexts() {
        for (int i = texts.size() - 1; i >= 0; i--) {
            if (texts.get(i).life <= 0) texts.remove(i);
        }
    }

    private void updateTexts(float dt) {
        for (int i = 0; i < texts.size(); i++) texts.get(i).update(dt);
    }

    private void resolveCollisions() {
        // 玩家弹幕 -> 怪物
        for (int i = 0; i < playerProj.size(); i++) {
            Projectile p = playerProj.get(i);
            if (!p.alive) continue;
            for (int j = 0; j < monsters.size(); j++) {
                Monster m = monsters.get(j);
                if (!m.alive) continue;
                if (p.collides(m)) {
                    m.takeDamage(p.damage, this);
                    audio.hit();
                    Particle.burst(this, p.x, p.y, Color.rgb(255, 230, 150), 4, 120f);
                    if (p.isAoE) {
                        for (Monster o : monsters) {
                            if (o != m && o.alive && p.distSq(o) <= p.aoeRadius * p.aoeRadius) {
                                o.takeDamage(p.damage * 0.6f, this);
                            }
                        }
                        p.alive = false;
                        break;
                    }
                    if (p.pierce > 0) {
                        p.pierce--;
                    } else {
                        p.alive = false;
                        break;
                    }
                }
            }
        }
        // 敌方弹幕 -> 玩家
        for (int i = 0; i < enemyProj.size(); i++) {
            Projectile p = enemyProj.get(i);
            if (!p.alive) continue;
            if (p.collides(player)) {
                player.takeDamage(p.damage, this);
                p.alive = false;
            }
        }
    }

    // ---------- 输入 ----------
    public boolean handleTouch(MotionEvent e) {
        if (state == State.MENU) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                start();
                return true;
            }
            return false;
        }
        if (state == State.GAMEOVER) {
            if (e.getAction() == MotionEvent.ACTION_UP && gameoverTime > 0.6f) {
                restart();
                return true;
            }
            return false;
        }
        if (state == State.PAUSED) {
            if (e.getAction() == MotionEvent.ACTION_UP) {
                resume();
                return true;
            }
            return false;
        }
        // 优先按钮
        boolean handled = btnMute.handle(e) | btnSkill1.handle(e) | btnSkill2.handle(e) | btnPill.handle(e);
        boolean joy = joystick.handle(e);
        return handled || joy;
    }

    // ---------- 渲染 ----------
    public void draw(Canvas canvas, Paint paint) {
        int sx = 0, sy = 0;
        if (shake > 0) {
            sx = (int) ((rng.nextFloat() - 0.5f) * 12f * shake / 0.6f);
            sy = (int) ((rng.nextFloat() - 0.5f) * 12f * shake / 0.6f);
        }
        canvas.save();
        canvas.translate(sx, sy);

        drawBackground(canvas, paint);
        if (state == State.MENU) {
            drawAmbientParticles(canvas, paint);
            drawMenu(canvas, paint);
            canvas.restore();
            return;
        }

        // 实体（按 y 排序近似深度）
        for (LootItem l : loot) l.draw(canvas, paint);
        for (Monster m : monsters) m.draw(canvas, paint);
        player.draw(canvas, paint);
        for (Projectile p : playerProj) p.draw(canvas, paint);
        for (Projectile p : enemyProj) p.draw(canvas, paint);
        drawParticles(canvas, paint);
        drawDamageTexts(canvas, paint);

        canvas.restore();

        // UI 不受抖动影响
        drawHud(canvas, paint);
        if (state == State.PLAYING) {
            joystick.draw(canvas, paint);
            btnSkill1.draw(canvas, paint);
            btnSkill2.draw(canvas, paint);
            btnPill.draw(canvas, paint);
            btnMute.draw(canvas, paint);
        }
        if (state == State.PAUSED) drawPauseOverlay(canvas, paint);
        if (state == State.GAMEOVER) drawGameoverOverlay(canvas, paint);
    }

    private void drawBackground(Canvas c, Paint p) {
        // 天空渐变
        Shader sg = new LinearGradient(0, 0, 0, height,
                Color.rgb(0x0E, 0x0B, 0x1A), Color.rgb(0x23, 0x14, 0x3A), Shader.TileMode.CLAMP);
        p.setShader(sg);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, width, height, p);
        p.setShader(null);

        // 月亮
        p.setColor(Color.argb(40, 255, 240, 200));
        c.drawCircle(width * 0.78f, height * 0.16f, 70f * density, p);
        p.setColor(Color.argb(220, 255, 245, 210));
        c.drawCircle(width * 0.78f, height * 0.16f, 42f * density, p);

        // 远山（多层）
        drawMountains(c, p, height * 0.34f, Color.rgb(0x18, 0x10, 0x2E), 60f * density, cloudOffset * 0.2f);
        drawMountains(c, p, height * 0.46f, Color.rgb(0x20, 0x16, 0x3A), 80f * density, cloudOffset * 0.3f);

        // 云雾
        p.setColor(Color.argb(18, 200, 200, 230));
        for (int i = 0; i < 6; i++) {
            float cx = ((cloudOffset * (10 + i * 3)) % (width + 200f)) - 100f;
            float cy = height * (0.2f + i * 0.07f);
            c.drawCircle(cx, cy, 50f * density, p);
            c.drawCircle(cx + 40f * density, cy + 10f, 40f * density, p);
        }

        // 地面
        p.setColor(Color.rgb(0x0A, 0x08, 0x14));
        c.drawRect(0, height - bottomBar - 30f * density, width, height, p);
        p.setColor(Color.argb(40, 0x6E, 0xB5, 0xFF));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        c.drawLine(0, height - bottomBar - 30f * density, width, height - bottomBar - 30f * density, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawMountains(Canvas c, Paint p, float baseY, int color, float amp, float offset) {
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        Path path = new Path();
        path.moveTo(0, height);
        path.lineTo(0, baseY);
        float step = width / 8f;
        for (int i = 0; i <= 8; i++) {
            float x = i * step;
            float y = baseY - amp * (float) Math.sin(i * 0.9 + offset * 0.05);
            path.lineTo(x, y);
        }
        path.lineTo(width, height);
        path.close();
        c.drawPath(path, p);
    }

    private void drawParticles(Canvas c, Paint p) {
        for (Particle part : particles) part.draw(c, p);
    }

    private void drawAmbientParticles(Canvas c, Paint p) {
        for (Particle part : particles) part.draw(c, p);
    }

    private void drawDamageTexts(Canvas c, Paint p) {
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        for (DamageText t : texts) {
            if (t.amount == 0) continue;
            int a = (int) (255 * Math.min(1f, t.life / 1f));
            p.setColor(Color.argb(a, 255, 230, 120));
            p.setTextSize(20f * density);
            c.drawText(String.valueOf(t.amount), t.x, t.y, p);
        }
    }

    // ---------- HUD ----------
    private void drawHud(Canvas c, Paint p) {
        float dp = density;
        // 顶部背景条
        p.setColor(Color.argb(140, 0, 0, 0));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, width, gameTop, p);

        // 境界
        p.setColor(Color.rgb(0xE8, 0xC2, 0x6A));
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(16f * dp);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(player.realm().name + player.layer + "重", 12f * dp, 24f * dp, p);

        // XP 条
        float bx = 12f * dp, by = 30f * dp, bw = width - 24f * dp, bh = 8f * dp;
        p.setColor(Color.argb(120, 0, 0, 0));
        c.drawRect(bx, by, bx + bw, by + bh, p);
        float xr = Math.min(1, player.xp / player.xpNeeded);
        p.setColor(Color.rgb(0x5B, 0xC8, 0x5B));
        c.drawRect(bx, by, bx + bw * xr, by + bh, p);
        p.setColor(Color.argb(120, 255, 255, 255));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        c.drawRect(bx, by, bx + bw, by + bh, p);
        p.setStyle(Paint.Style.FILL);

        // HP / MP 条
        drawBar(c, p, bx, by + 14f * dp, bw / 2 - 4f, bh, player.hp, player.maxHp, Color.rgb(0xD2, 0x3B, 0x3B));
        drawBar(c, p, bx + bw / 2 + 4f, by + 14f * dp, bw / 2 - 4f, bh, player.mp, player.maxMp, Color.rgb(0x3B, 0x7D, 0xD2));

        // 右上信息：灵石、击杀、波次
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(13f * dp);
        p.setColor(Color.WHITE);
        c.drawText("波次 " + wave, width - 12f * dp - 40f * dp, 22f * dp, p);
        c.drawText("灵石 " + player.lingshi, width - 12f * dp - 40f * dp, 40f * dp, p);
        c.drawText("斩妖 " + player.killCount, width - 12f * dp - 40f * dp, 58f * dp, p);

        // 装备简报（左下角小字）
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(10f * dp);
        p.setColor(Color.argb(200, 200, 200, 220));
        float ey = height - bottomBar - 18f * dp;
        c.drawText("剑 " + equipName(player.weapon), 12f * dp, ey, p);
        c.drawText("袍 " + equipName(player.armor), 12f * dp + 120f * dp, ey, p);
        c.drawText("玉 " + equipName(player.accessory), 12f * dp + 240f * dp, ey, p);

        // 丹药数量
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(Color.WHITE);
        p.setTextSize(12f * dp);
        c.drawText("×" + player.pillCount, btnPill.cx + btnPill.radius + 14f * dp, btnPill.cy + 4f * dp, p);

        // 历史最高
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(Color.argb(160, 200, 200, 220));
        p.setTextSize(11f * dp);
        c.drawText("最高 " + bestWave + " 波", 12f * dp, 78f * dp, p);
    }

    private String equipName(LootItem it) {
        return it == null ? "无" : it.rarity.label;
    }

    private void drawBar(Canvas c, Paint p, float x, float y, float w, float h, float cur, float max, int color) {
        p.setColor(Color.argb(120, 0, 0, 0));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(x, y, x + w, y + h, p);
        float r = max <= 0 ? 0 : Math.max(0, Math.min(1, cur / max));
        p.setColor(color);
        c.drawRect(x, y, x + w * r, y + h, p);
        p.setColor(Color.argb(120, 255, 255, 255));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        c.drawRect(x, y, x + w, y + h, p);
        p.setStyle(Paint.Style.FILL);
    }

    // ---------- 菜单/暂停/结束覆盖层 ----------
    private void drawMenu(Canvas c, Paint p) {
        float dp = density;
        // 半透明遮罩
        p.setColor(Color.argb(120, 0, 0, 0));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, width, height, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        // 标题
        p.setColor(Color.rgb(0xE8, 0xC2, 0x6A));
        p.setTextSize(54f * dp);
        c.drawText("仙途·传奇", width / 2f, height * 0.32f, p);
        p.setColor(Color.rgb(0x9C, 0xB0, 0xE0));
        p.setTextSize(16f * dp);
        c.drawText("一念起，万法生", width / 2f, height * 0.32f + 34f * dp, p);

        // 提示
        float pulse = 0.5f + 0.5f * (float) Math.sin(menuTime * 3f);
        p.setColor(Color.argb((int) (160 + 95 * pulse), 255, 255, 255));
        p.setTextSize(20f * dp);
        c.drawText("— 点击屏幕开始修炼 —", width / 2f, height * 0.6f, p);

        p.setColor(Color.argb(180, 200, 200, 220));
        p.setTextSize(13f * dp);
        p.setTypeface(Typeface.DEFAULT);
        c.drawText("左下摇杆移动  ·  右下按钮施法/服丹", width / 2f, height * 0.7f, p);
        c.drawText("自动攻击最近妖兽，击杀获取经验与灵石", width / 2f, height * 0.7f + 22f * dp, p);
        c.drawText("攒满经验突破境界，每 5 波将现渡劫妖王", width / 2f, height * 0.7f + 44f * dp, p);
    }

    private void drawPauseOverlay(Canvas c, Paint p) {
        float dp = density;
        p.setColor(Color.argb(160, 0, 0, 0));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, width, height, p);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(40f * dp);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("— 闭关中 —", width / 2f, height / 2f, p);
        p.setTextSize(16f * dp);
        p.setTypeface(Typeface.DEFAULT);
        c.drawText("点击屏幕继续修炼", width / 2f, height / 2f + 40f * dp, p);
    }

    private void drawGameoverOverlay(Canvas c, Paint p) {
        float dp = density;
        p.setColor(Color.argb(160, 10, 0, 0));
        p.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, width, height, p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setColor(Color.rgb(0xE2, 0x4B, 0x2A));
        p.setTextSize(50f * dp);
        c.drawText("仙 陨", width / 2f, height * 0.34f, p);

        p.setColor(Color.WHITE);
        p.setTextSize(16f * dp);
        p.setTypeface(Typeface.DEFAULT);
        CultivationRealm r = player.realm();
        c.drawText("境界：" + r.name + player.layer + "重", width / 2f, height * 0.34f + 44f * dp, p);
        c.drawText("斩妖：" + player.killCount + "  灵石：" + player.lingshi, width / 2f, height * 0.34f + 68f * dp, p);
        c.drawText("通关波次：" + wave + "  历史最高：" + bestWave, width / 2f, height * 0.34f + 92f * dp, p);

        float pulse = 0.5f + 0.5f * (float) Math.sin(gameoverTime * 3f);
        p.setColor(Color.argb((int) (160 + 95 * pulse), 255, 255, 255));
        p.setTextSize(20f * dp);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("— 点击屏幕 再入仙途 —", width / 2f, height * 0.62f, p);
    }

    // ---------- 伤害飘字 ----------
    private static class DamageText {
        float x, y;
        int amount;
        float life = 0.9f;
        float vy = -50f;
        DamageText(float x, float y, int amount) {
            this.x = x;
            this.y = y;
            this.amount = amount;
        }

        void update(float dt) {
            y += vy * dt;
            life -= dt;
        }
    }

}

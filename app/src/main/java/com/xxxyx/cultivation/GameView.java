package com.xxxyx.cultivation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.xxxyx.cultivation.audio.GameAudio;
import com.xxxyx.cultivation.game.GameWorld;

/**
 * 游戏视图：SurfaceView + 固定步长渲染线程。
 * 由 MainActivity 宿主，负责生命周期与输入分发。
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    private final GameWorld world;
    private final GameAudio audio;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private GameThread thread;
    private boolean surfaceReady = false;

    public GameView(Context context) {
        super(context);
        this.audio = new GameAudio(context);
        this.world = new GameWorld(context, audio);
        getHolder().addCallback(this);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public GameWorld getWorld() {
        return world;
    }

    public void pause() {
        world.pause();
        stopThread();
    }

    public void resume() {
        world.resume();
        startThread();
    }

    public void destroy() {
        audio.release();
    }

    private synchronized void startThread() {
        if (thread != null && thread.isAlive()) return;
        if (!surfaceReady) return;
        thread = new GameThread();
        thread.setRunning(true);
        thread.start();
    }

    private synchronized void stopThread() {
        if (thread == null) return;
        thread.setRunning(false);
        boolean retry = true;
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {
                // 继续
            }
        }
        thread = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        world.handleTouch(event);
        return true;
    }

    // ---------- SurfaceHolder.Callback ----------
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        world.resize(width, height);
        surfaceReady = true;
        startThread();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stopThread();
    }

    // ---------- 渲染线程 ----------
    private class GameThread extends Thread {
        private volatile boolean running = false;
        private static final long STEP_NS = 1_000_000_000L / 60; // 60fps
        private long lastTime = 0;

        void setRunning(boolean r) {
            running = r;
        }

        @Override
        public void run() {
            lastTime = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                long elapsed = now - lastTime;
                lastTime = now;
                // 单帧 dt，最大 50ms 防止卡顿后跳跃
                float dt = Math.min(elapsed, 50_000_000L) / 1_000_000_000f;
                world.update(dt);

                SurfaceHolder holder = getHolder();
                Canvas canvas = null;
                try {
                    canvas = holder.lockCanvas(null);
                    if (canvas != null) {
                        synchronized (holder) {
                            world.draw(canvas, paint);
                        }
                    }
                } catch (Exception e) {
                    // 偶发的 lockCanvas 失败，忽略
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas);
                        } catch (Exception ignored) {
                        }
                    }
                }
                // 简单限帧，避免吃满 CPU
                long frameTime = System.nanoTime() - now;
                long sleepNs = STEP_NS - frameTime;
                if (sleepNs > 2_000_000L) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000L);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }
    }
}

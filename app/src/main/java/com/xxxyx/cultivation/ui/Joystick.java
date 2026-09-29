package com.xxxyx.cultivation.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;

/**
 * 虚拟摇杆。固定位置，支持多点触控中独立追踪一个 pointerId。
 * 输出归一化方向向量 (-1..1) 与幅值。
 */
public class Joystick {
    public float cx, cy;
    public float baseRadius;
    public float knobRadius;
    private int pointerId = -1;
    private float dx = 0f, dy = 0f;
    private float mag = 0f;

    public Joystick(float cx, float cy, float baseRadius, float knobRadius) {
        this.cx = cx;
        this.cy = cy;
        this.baseRadius = baseRadius;
        this.knobRadius = knobRadius;
    }

    public void layout(float cx, float cy, float base, float knob) {
        this.cx = cx;
        this.cy = cy;
        this.baseRadius = base;
        this.knobRadius = knob;
    }

    /** 处理触摸，返回 true 表示消费了该事件 */
    public boolean handle(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float ex = e.getX(idx);
                float ey = e.getY(idx);
                if (pointerId == -1 && inBase(ex, ey)) {
                    pointerId = e.getPointerId(idx);
                    updateKnob(e);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (pointerId != -1) {
                    int p = e.findPointerIndex(pointerId);
                    if (p != -1) {
                        updateKnob(e.getX(p), e.getY(p));
                    }
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int upId = e.getPointerId(idx);
                if (upId == pointerId) {
                    pointerId = -1;
                    dx = 0; dy = 0; mag = 0;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                pointerId = -1;
                dx = 0; dy = 0; mag = 0;
                break;
            }
        }
        return false;
    }

    private void updateKnob(MotionEvent e) {
        int p = e.findPointerIndex(pointerId);
        if (p != -1) updateKnob(e.getX(p), e.getY(p));
    }

    private void updateKnob(float ex, float ey) {
        float vx = ex - cx;
        float vy = ey - cy;
        float d = (float) Math.sqrt(vx * vx + vy * vy);
        float max = baseRadius;
        float cl = d > max ? max : d;
        float nx = d == 0 ? 0 : vx / d;
        float ny = d == 0 ? 0 : vy / d;
        dx = nx * cl;
        dy = ny * cl;
        mag = cl / max;
    }

    private boolean inBase(float ex, float ey) {
        float vx = ex - cx;
        float vy = ey - cy;
        // 略大于底圈，便于按下
        float r = baseRadius * 1.4f;
        return vx * vx + vy * vy <= r * r;
    }

    public float dirX() {
        return mag > 0.05f ? dx / baseRadius : 0f;
    }

    public float dirY() {
        return mag > 0.05f ? dy / baseRadius : 0f;
    }

    public float mag() {
        return mag > 0.05f ? mag : 0f;
    }

    public boolean active() {
        return pointerId != -1;
    }

    public void draw(Canvas c, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(50, 255, 255, 255));
        c.drawCircle(cx, cy, baseRadius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(140, 255, 255, 255));
        p.setStrokeWidth(3f);
        c.drawCircle(cx, cy, baseRadius, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(200, 230, 210, 140));
        float kx = cx + dx;
        float ky = cy + dy;
        c.drawCircle(kx, ky, knobRadius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(180, 255, 255, 255));
        c.drawCircle(kx, ky, knobRadius, p);
    }
}

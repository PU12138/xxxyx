package com.xxxyx.cultivation.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;

/**
 * 技能/物品按钮。支持冷却扇形遮罩、按下高亮、单击触发。
 */
public class ActionButton {
    public float cx, cy;
    public float radius;
    public String label;
    public final int color;
    private boolean pressed = false;
    private boolean justPressed = false;
    private int pointerId = -1;
    /** 0..1 冷却进度，1 表示完全冷却中，0 表示可用 */
    public float cooldownRatio = 0f;
    public boolean enabled = true;

    public ActionButton(float cx, float cy, float radius, String label, int color) {
        this.cx = cx;
        this.cy = cy;
        this.radius = radius;
        this.label = label;
        this.color = color;
    }

    public void layout(float cx, float cy, float r) {
        this.cx = cx;
        this.cy = cy;
        this.radius = r;
    }

    public boolean handle(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float ex = e.getX(idx);
                float ey = e.getY(idx);
                if (pointerId == -1 && hit(ex, ey)) {
                    pointerId = e.getPointerId(idx);
                    pressed = true;
                    if (enabled) justPressed = true;
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int upId = e.getPointerId(idx);
                if (upId == pointerId) {
                    pointerId = -1;
                    pressed = false;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                pressed = false;
                pointerId = -1;
                break;
            }
        }
        return false;
    }

    private boolean hit(float ex, float ey) {
        float vx = ex - cx;
        float vy = ey - cy;
        return vx * vx + vy * vy <= radius * radius;
    }

    /** 消费一次“按下”事件（边沿触发） */
    public boolean consumeJustPressed() {
        if (justPressed) {
            justPressed = false;
            return true;
        }
        return false;
    }

    public void draw(Canvas c, Paint p) {
        RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        p.setStyle(Paint.Style.FILL);
        int bg = enabled ? color : Color.argb(120, 90, 90, 90);
        if (pressed && enabled) bg = Color.argb(220, Color.red(color), Color.green(color), Color.blue(color));
        p.setColor(bg);
        c.drawCircle(cx, cy, radius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(Color.argb(180, 255, 255, 255));
        p.setStrokeWidth(3f);
        c.drawCircle(cx, cy, radius, p);

        // 冷却遮罩（扇形）
        if (cooldownRatio > 0.001f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(160, 0, 0, 0));
            float sweep = 360f * cooldownRatio;
            c.drawArc(rect, -90f, sweep, true, p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(radius * 0.5f);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText(label, cx, cy + radius * 0.18f, p);
    }
}

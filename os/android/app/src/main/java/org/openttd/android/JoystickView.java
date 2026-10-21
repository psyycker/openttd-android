/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * On-screen joystick to scroll the map, shown over the game. Only touches
 * that start on the joystick are taken; other fingers keep driving the cursor.
 */
public class JoystickView extends View {
    private final Paint base_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring_paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob_paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Position of the knob relative to the centre, -1..1. */
    private float knob_x = 0, knob_y = 0;
    /** Pointer currently moving the knob, or -1. */
    private int pointer_id = -1;

    /** Pass the joystick position to the game; see android_main.cpp. */
    private static native void nativeSetJoystick(float x, float y);

    public JoystickView(Context context) {
        super(context);
        base_paint.setColor(0x40000000);
        ring_paint.setColor(0x80FFFFFF);
        ring_paint.setStyle(Paint.Style.STROKE);
        ring_paint.setStrokeWidth(2 * getResources().getDisplayMetrics().density);
        knob_paint.setColor(0xB0FFFFFF);
    }

    private float radius() {
        return Math.min(getWidth(), getHeight()) / 2.0f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2.0f;
        float cy = getHeight() / 2.0f;
        float r = radius();
        canvas.drawCircle(cx, cy, r - ring_paint.getStrokeWidth(), base_paint);
        canvas.drawCircle(cx, cy, r - ring_paint.getStrokeWidth(), ring_paint);
        float knob_travel = r * 0.6f;
        canvas.drawCircle(cx + knob_x * knob_travel, cy + knob_y * knob_travel, r * 0.4f, knob_paint);
    }

    private void moveKnob(float x, float y) {
        float r = radius();
        float dx = (x - getWidth() / 2.0f) / r;
        float dy = (y - getHeight() / 2.0f) / r;
        float len = (float)Math.hypot(dx, dy);
        if (len > 1) {
            dx /= len;
            dy /= len;
        }
        setKnob(dx, dy);
    }

    private void setKnob(float x, float y) {
        knob_x = x;
        knob_y = y;
        nativeSetJoystick(x, y);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                /* Only react to touches on the round joystick itself, not its square corners. */
                float r = radius();
                if (Math.hypot(event.getX() - getWidth() / 2.0f, event.getY() - getHeight() / 2.0f) > r) return false;
                pointer_id = event.getPointerId(0);
                moveKnob(event.getX(), event.getY());
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(pointer_id);
                if (index >= 0) moveKnob(event.getX(index), event.getY(index));
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pointer_id = -1;
                setKnob(0, 0);
                return true;

            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setKnob(0, 0);
    }
}

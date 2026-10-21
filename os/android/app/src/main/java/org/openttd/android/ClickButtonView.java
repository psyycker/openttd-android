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
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

/**
 * On-screen button: a left or right mouse button, or a key. It acts like the
 * real thing: held down while touched, so another finger can move the cursor
 * to drag, or click while a modifier key is held.
 */
public class ClickButtonView extends View {
    /** What the button does when pressed or released. */
    interface Action {
        void set(boolean down);
    }

    private final String label;
    private final Action action;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean pressed = false;

    /** Pass a press or release to the game; see android_main.cpp. */
    private static native void nativeMouseButton(int button, boolean down);

    /** Press or release a mouse button: 0 for left, 1 for right. */
    static Action mouse(int button) {
        return down -> nativeMouseButton(button, down);
    }

    /** Press or release a key, with the given modifiers held around it. */
    static Action key(int key, boolean ctrl, boolean shift, boolean alt) {
        return down -> {
            int[] modifiers = { ctrl ? KeyEvent.KEYCODE_CTRL_LEFT : 0, shift ? KeyEvent.KEYCODE_SHIFT_LEFT : 0, alt ? KeyEvent.KEYCODE_ALT_LEFT : 0 };
            if (down) {
                for (int m : modifiers) if (m != 0) SDLActivity.onNativeKeyDown(m);
                SDLActivity.onNativeKeyDown(key);
            } else {
                SDLActivity.onNativeKeyUp(key);
                for (int m : modifiers) if (m != 0) SDLActivity.onNativeKeyUp(m);
            }
        };
    }

    public ClickButtonView(Context context, String label, String description, Action action) {
        super(context);
        this.label = label;
        this.action = action;
        float density = getResources().getDisplayMetrics().density;
        ring.setColor(0x80FFFFFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2 * density);
        text.setColor(0xD0FFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setContentDescription(description);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2.0f;
        float cy = getHeight() / 2.0f;
        float r = Math.min(cx, cy) - ring.getStrokeWidth();
        fill.setColor(pressed ? 0x90FFFFFF : 0x40000000);
        canvas.drawCircle(cx, cy, r, fill);
        canvas.drawCircle(cx, cy, r, ring);
        /* Shrink longer labels to fit inside the circle. */
        text.setTextSize(r * 0.8f);
        float width = text.measureText(label);
        if (width > r * 1.5f) text.setTextSize(r * 0.8f * r * 1.5f / width);
        canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2, text);
    }

    private void setPressed(boolean down, boolean notify) {
        if (pressed == down) return;
        pressed = down;
        if (notify) action.set(down);
        if (down) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                /* Only react to touches on the round button itself. */
                float r = Math.min(getWidth(), getHeight()) / 2.0f;
                if (Math.hypot(event.getX() - getWidth() / 2.0f, event.getY() - getHeight() / 2.0f) > r) return false;
                setPressed(true, true);
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressed(false, true);
                return true;

            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        setPressed(false, true);
    }
}

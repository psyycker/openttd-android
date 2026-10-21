/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings for the touch controls of the app. */
final class Controls {
    private static final String PREFS = "controls";
    /** Old setting: OFF, LEFT or RIGHT; replaced by KEY_SIDE and KEY_JOYSTICK_SHOWN. */
    private static final String KEY_JOYSTICK = "joystick";
    private static final String KEY_SIDE = "side";
    private static final String KEY_JOYSTICK_SHOWN = "joystick_shown";
    private static final String KEY_CLICK_BUTTONS = "click_buttons";

    /** Which bottom corner the on-screen controls are in, e.g. for left or right handed play. */
    enum Side { LEFT, RIGHT }

    private Controls() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Side getSide(Context context) {
        String old = prefs(context).getString(KEY_JOYSTICK, null);
        String fallback = "RIGHT".equals(old) ? Side.RIGHT.name() : Side.LEFT.name();
        try {
            return Side.valueOf(prefs(context).getString(KEY_SIDE, fallback));
        } catch (IllegalArgumentException e) {
            return Side.LEFT;
        }
    }

    static void setSide(Context context, Side side) {
        prefs(context).edit().putString(KEY_SIDE, side.name()).apply();
    }

    /** Whether to show the on-screen joystick to scroll the map. */
    static boolean getJoystick(Context context) {
        String old = prefs(context).getString(KEY_JOYSTICK, null);
        return prefs(context).getBoolean(KEY_JOYSTICK_SHOWN, old != null && !"OFF".equals(old));
    }

    static void setJoystick(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_JOYSTICK_SHOWN, show).apply();
    }

    /** Whether to show on-screen left and right mouse buttons. */
    static boolean getClickButtons(Context context) {
        return prefs(context).getBoolean(KEY_CLICK_BUTTONS, false);
    }

    static void setClickButtons(Context context, boolean show) {
        prefs(context).edit().putBoolean(KEY_CLICK_BUTTONS, show).apply();
    }
}

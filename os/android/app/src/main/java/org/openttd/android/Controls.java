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
    private static final String KEY_JOYSTICK = "joystick";

    /** Where to show the on-screen joystick for scrolling the map. */
    enum Joystick { OFF, LEFT, RIGHT }

    private Controls() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static Joystick getJoystick(Context context) {
        try {
            return Joystick.valueOf(prefs(context).getString(KEY_JOYSTICK, Joystick.OFF.name()));
        } catch (IllegalArgumentException e) {
            return Joystick.OFF;
        }
    }

    static void setJoystick(Context context, Joystick joystick) {
        prefs(context).edit().putString(KEY_JOYSTICK, joystick.name()).apply();
    }
}

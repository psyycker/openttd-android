/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.content.Intent;
import android.os.Bundle;

import org.libsdl.app.SDLActivity;

/**
 * The game itself. The native code lives in libmain.so (built from the
 * OpenTTD CMake project); SDL's activity takes care of the surface, input,
 * audio and the native main thread.
 */
public class OpenTTDActivity extends SDLActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /* The process may be recreated straight into this activity, e.g. after
         * the app was updated; make sure the game data is in place first. */
        if (!DataInstaller.isInstalled(this)) {
            super.onCreate(savedInstanceState);
            startActivity(new Intent(this, LauncherActivity.class));
            finish();
            return;
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected String[] getLibraries() {
        /* SDL2 and all other dependencies are linked statically into libmain.so. */
        return new String[] { "main" };
    }
}

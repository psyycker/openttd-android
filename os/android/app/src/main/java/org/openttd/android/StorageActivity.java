/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The app settings as a screen of their own, for the launcher shortcut
 * (long-press the app icon). In game they are shown as a dialog instead.
 */
public class StorageActivity extends Activity implements AppSettings.Host {
    private AppSettings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_settings_title);
        settings = new AppSettings(this, this);
        setContentView(settings.createView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        settings.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        settings.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean isGameRunning() {
        return OpenTTDActivity.isRunning();
    }

    @Override
    public void restartGame() {
        /* The game is in the background, and was saved when it went there. */
        RestartActivity.restart(this);
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    public void close() {
        finish();
    }
}

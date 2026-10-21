/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import org.libsdl.app.SDL;
import org.libsdl.app.SDLActivity;

/**
 * The game itself. The native code lives in libmain.so (built from the
 * OpenTTD CMake project); SDL's activity takes care of the surface, input,
 * audio and the native main thread.
 */
public class OpenTTDActivity extends SDLActivity {
    /** Whether the game is running in this process. */
    private static volatile boolean running = false;

    static boolean isRunning() {
        return running;
    }

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
        /* Must be done before the native code starts. */
        StorageLocation.applyForNative(this);
        running = true;
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        running = false;
        super.onDestroy();
    }

    /**
     * Command line arguments for OpenTTD can be passed for debugging, e.g.
     * {@code adb shell am start -n org.openttd.android/.LauncherActivity --es args "-d driver=3"}.
     */
    @Override
    protected String[] getArguments() {
        String args = getIntent().getStringExtra(LauncherActivity.EXTRA_ARGS);
        if (args == null || args.trim().isEmpty()) return new String[0];
        return args.trim().split("\\s+");
    }

    /** The app settings dialog, while shown. */
    private Dialog settings_dialog;
    private AppSettings settings;

    /** Called from the game's menus (ShowAndroidAppSettings in android_main.cpp), on the game thread. */
    public void openAppSettings() {
        runOnUiThread(this::showAppSettings);
    }

    /** Show the app settings as a full screen dialog over the game; the game pauses meanwhile. */
    private void showAppSettings() {
        if (settings_dialog != null) return;

        settings = new AppSettings(this, new AppSettings.Host() {
            @Override
            public boolean isGameRunning() {
                return true;
            }

            @Override
            public void restartGame() {
                /* The game saves and quits once it runs again; then android_main.cpp calls restartApp. */
                nativeRequestRestart();
                close();
            }

            @Override
            public void close() {
                if (settings_dialog != null) settings_dialog.dismiss();
            }

            @Override
            public void onControlsChanged() {
                updateJoystick();
            }
        });

        settings_dialog = new Dialog(this, R.style.AppSettingsDialog);
        settings_dialog.setContentView(settings.createView(settings_dialog.getContext()));
        settings_dialog.setOnDismissListener(d -> {
            settings_dialog = null;
            settings = null;
        });
        Window window = settings_dialog.getWindow();
        if (window != null) window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        settings_dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settings != null) settings.onResume();
        updateJoystick();
    }

    /** The on-screen joystick for scrolling the map, while shown. */
    private JoystickView joystick;
    /** The on-screen left and right mouse buttons, while shown. */
    private ClickButtonView left_button, right_button;

    /** Show, move or hide the on-screen joystick and mouse buttons, following the settings. */
    private void updateJoystick() {
        if (mLayout == null) return;
        for (View v : new View[] { joystick, left_button, right_button }) {
            if (v != null) mLayout.removeView(v);
        }
        joystick = null;
        left_button = right_button = null;

        boolean show_joystick = Controls.getJoystick(this);
        boolean buttons = Controls.getClickButtons(this);
        boolean on_left = Controls.getSide(this) == Controls.Side.LEFT;
        float density = getResources().getDisplayMetrics().density;
        int margin = (int)(24 * density);

        if (show_joystick) {
            int size = (int)(140 * density);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(size, size);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            params.addRule(on_left ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
            params.setMargins(margin, margin, margin, margin);

            joystick = new JoystickView(this);
            joystick.setId(View.generateViewId());
            mLayout.addView(joystick, params);
        }

        if (buttons) {
            /* Side by side above the joystick, or stacked in the corner without joystick. */
            int size = (int)(64 * density);
            int gap = (int)(12 * density);

            RelativeLayout.LayoutParams right_params = new RelativeLayout.LayoutParams(size, size);
            RelativeLayout.LayoutParams left_params = new RelativeLayout.LayoutParams(size, size);
            right_button = new ClickButtonView(this, ClickButtonView.Button.RIGHT);
            right_button.setId(View.generateViewId());
            left_button = new ClickButtonView(this, ClickButtonView.Button.LEFT);

            if (joystick == null) {
                right_params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                right_params.addRule(on_left ? RelativeLayout.ALIGN_PARENT_LEFT : RelativeLayout.ALIGN_PARENT_RIGHT);
                right_params.setMargins(margin, margin, margin, margin);
                left_params.addRule(RelativeLayout.ABOVE, right_button.getId());
                left_params.addRule(RelativeLayout.ALIGN_LEFT, right_button.getId());
                left_params.setMargins(0, gap, 0, gap);
            } else {
                left_params.addRule(RelativeLayout.ABOVE, joystick.getId());
                left_params.addRule(RelativeLayout.ALIGN_LEFT, joystick.getId());
                left_params.setMargins(0, 0, 0, gap);
                right_params.addRule(RelativeLayout.ABOVE, joystick.getId());
                right_params.addRule(RelativeLayout.ALIGN_RIGHT, joystick.getId());
                right_params.setMargins(0, 0, 0, gap);
            }
            mLayout.addView(right_button, right_params);
            mLayout.addView(left_button, left_params);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (settings != null) settings.onActivityResult(requestCode, resultCode, data);
    }

    /** Ask the game to save and quit, so it can be restarted; see android_main.cpp. */
    private static native void nativeRequestRestart();

    /** Called from android_main.cpp, after the game quit because of #nativeRequestRestart. */
    public static void restartApp() {
        RestartActivity.restart(SDL.getContext());
    }

    @Override
    protected String[] getLibraries() {
        /* SDL2 and all other dependencies are linked statically into libmain.so. */
        return new String[] { "main" };
    }
}

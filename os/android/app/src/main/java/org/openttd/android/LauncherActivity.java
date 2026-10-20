/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Entry point: makes sure the game data is extracted, then starts the game. */
public class LauncherActivity extends Activity {
    /** Intent extra with command line arguments for OpenTTD; see {@link OpenTTDActivity#getArguments()}. */
    static final String EXTRA_ARGS = "args";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DataInstaller.isInstalled(this)) {
            startGame();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int)(32 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView label = new TextView(this);
        label.setText(R.string.installing_data);
        label.setGravity(Gravity.CENTER);
        layout.addView(label);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        layout.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(layout);

        new Thread(() -> {
            try {
                DataInstaller.install(this, (done, total) -> runOnUiThread(() -> {
                    bar.setMax(total);
                    bar.setProgress(done);
                }));
                runOnUiThread(this::startGame);
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(R.string.app_name)
                        .setMessage(getString(R.string.install_failed, e.toString()))
                        .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                        .setCancelable(false)
                        .show());
            }
        }, "openttd-install").start();
    }

    private void startGame() {
        Intent intent = new Intent(this, OpenTTDActivity.class);
        String args = getIntent().getStringExtra(EXTRA_ARGS);
        if (args != null) intent.putExtra(EXTRA_ARGS, args);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}

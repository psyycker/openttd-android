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
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/** Entry point: makes sure the game data is extracted, then starts the game. */
public class LauncherActivity extends Activity {
    /** Intent extra with command line arguments for OpenTTD; see {@link OpenTTDActivity#getArguments()}. */
    static final String EXTRA_ARGS = "args";

    private TextView label;
    private ProgressBar bar;
    /** Waiting for the user to grant "All files access" for the public folder. */
    private boolean waiting_for_permission = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (DataInstaller.isInstalled(this) && StorageLocation.wasAsked(this) && StorageLocation.getPendingCopy(this) == null) {
            startGame();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int)(32 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        label = new TextView(this);
        label.setGravity(Gravity.CENTER);
        layout.addView(label);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        layout.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(layout);

        if (DataInstaller.isInstalled(this)) {
            askStorageLocation();
        } else {
            installData();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* Back from the permission screen. */
        if (waiting_for_permission) {
            waiting_for_permission = false;
            if (StorageLocation.hasAllFilesAccess(this)) {
                usePublicFolder();
            } else {
                StorageLocation.setAsked(this);
                Toast.makeText(this, R.string.location_later, Toast.LENGTH_LONG).show();
                startGame();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        /* Handled in onResume, which is called after this. */
    }

    /** Extract the game data from the APK, then continue the start up. */
    private void installData() {
        label.setText(R.string.installing_data);
        new Thread(() -> {
            try {
                DataInstaller.install(this, (done, total) -> runOnUiThread(() -> {
                    bar.setMax(total);
                    bar.setProgress(done);
                }));
                runOnUiThread(this::askStorageLocation);
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

    /** Ask once whether to keep the personal files in a folder that is easy to reach. */
    private void askStorageLocation() {
        if (StorageLocation.wasAsked(this)) {
            startGame();
            return;
        }

        bar.setVisibility(ProgressBar.INVISIBLE);
        label.setText("");
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_title)
                .setMessage(getString(R.string.location_question, StorageLocation.getPublicDir().getAbsolutePath()))
                .setPositiveButton(R.string.location_public, (d, w) -> {
                    if (StorageLocation.hasAllFilesAccess(this)) {
                        usePublicFolder();
                    } else {
                        waiting_for_permission = true;
                        StorageLocation.requestAllFilesAccess(this);
                    }
                })
                .setNegativeButton(R.string.location_app, (d, w) -> {
                    StorageLocation.setAsked(this);
                    startGame();
                })
                .setCancelable(false)
                .show();
    }

    /** Copy the personal files to the new storage location, then start the game. */
    private void copyThenStart(File from, File to) {
        label.setText(R.string.storage_copying);
        bar.setVisibility(ProgressBar.VISIBLE);
        bar.setIndeterminate(true);

        new Thread(() -> {
            try {
                StorageLocation.copyContents(from, to);
                runOnUiThread(this::startGame);
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(R.string.storage_title)
                        .setMessage(getString(R.string.storage_copy_failed_start, e.getMessage()))
                        .setPositiveButton(android.R.string.ok, (d, w) -> startGame())
                        .setCancelable(false)
                        .show());
            }
        }, "openttd-copy").start();
    }

    /** Switch to the public folder, copying the files from the app folder. */
    private void usePublicFolder() {
        File from = StorageLocation.getDefaultDir(this);
        File to = StorageLocation.getPublicDir();
        label.setText(R.string.storage_copying);
        bar.setVisibility(ProgressBar.VISIBLE);
        bar.setIndeterminate(true);

        new Thread(() -> {
            try {
                if (!to.isDirectory() && !to.mkdirs()) throw new IOException(to.getAbsolutePath());
                StorageLocation.copyContents(from, to);
                StorageLocation.setCustomDir(this, to);
                StorageLocation.setAsked(this);
                runOnUiThread(this::startGame);
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle(R.string.location_title)
                        .setMessage(getString(R.string.storage_copy_failed, e.getMessage()))
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            StorageLocation.setAsked(this);
                            startGame();
                        })
                        .setCancelable(false)
                        .show());
            }
        }, "openttd-copy").start();
    }

    private void startGame() {
        /* Files to bring along to a newly chosen storage location? */
        File copy_from = StorageLocation.getPendingCopy(this);
        if (copy_from != null) {
            StorageLocation.setPendingCopy(this, null);
            if (copy_from.isDirectory()) {
                copyThenStart(copy_from, StorageLocation.getEffectiveDir(this));
                return;
            }
        }

        Intent intent = new Intent(this, OpenTTDActivity.class);
        String args = getIntent().getStringExtra(EXTRA_ARGS);
        if (args != null) intent.putExtra(EXTRA_ARGS, args);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}

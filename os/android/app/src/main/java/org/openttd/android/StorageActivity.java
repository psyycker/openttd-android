/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Lets the user choose where OpenTTD keeps its personal files, e.g. a folder
 * in Documents so savegames and content can be managed with any file manager.
 * Reachable through the launcher shortcut (long-press the app icon).
 */
public class StorageActivity extends Activity {
    private static final int REQUEST_PICK_FOLDER = 1;

    private TextView current;
    private TextView status;
    private Button grant;
    /** Folder picked by the user, waiting for the permission to be granted. */
    private File pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_settings_title);

        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText(R.string.app_settings_title);
        title.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
        title.setPadding(0, 0, 0, pad);
        layout.addView(title);

        TextView section = new TextView(this);
        section.setText(R.string.storage_title);
        section.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        section.setPadding(0, 0, 0, pad / 2);
        layout.addView(section);

        TextView intro = new TextView(this);
        intro.setText(R.string.storage_intro);
        layout.addView(intro);

        current = new TextView(this);
        current.setPadding(0, pad, 0, pad);
        current.setTextIsSelectable(true);
        layout.addView(current);

        Button use_public = new Button(this);
        use_public.setText(getString(R.string.storage_use_public, StorageLocation.getPublicDir().getAbsolutePath()));
        use_public.setOnClickListener(v -> onFolderChosen(StorageLocation.getPublicDir()));
        layout.addView(use_public);

        Button choose = new Button(this);
        choose.setText(R.string.storage_choose);
        choose.setOnClickListener(v -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_PICK_FOLDER));
        layout.addView(choose);

        Button reset = new Button(this);
        reset.setText(R.string.storage_reset);
        reset.setOnClickListener(v -> changeTo(null));
        layout.addView(reset);

        status = new TextView(this);
        status.setPadding(0, pad, 0, 0);
        layout.addView(status);

        grant = new Button(this);
        grant.setText(R.string.storage_grant);
        grant.setOnClickListener(v -> requestAllFilesAccess());
        layout.addView(grant);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        /* The app is drawn edge-to-edge; keep the content clear of the system bars. */
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();

        /* Back from the permission screen? */
        if (pending != null && StorageLocation.hasAllFilesAccess(this)) {
            File dir = pending;
            pending = null;
            changeTo(dir);
        }
        updateViews();
    }

    private void updateViews() {
        File custom = StorageLocation.getCustomDir(this);
        File effective = StorageLocation.getEffectiveDir(this);
        current.setText(getString(R.string.storage_current, effective.getAbsolutePath()));

        boolean missing_permission = custom != null && !StorageLocation.hasAllFilesAccess(this);
        status.setText(missing_permission ? getString(R.string.storage_permission_missing, custom.getAbsolutePath()) : "");
        grant.setVisibility(missing_permission ? Button.VISIBLE : Button.GONE);
    }

    private void requestAllFilesAccess() {
        StorageLocation.requestAllFilesAccess(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        /* Handled in onResume, which is called after this. */
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        File dir = StorageLocation.treeUriToDir(data.getData());
        if (dir == null) {
            new AlertDialog.Builder(this).setMessage(R.string.storage_unsupported).setPositiveButton(android.R.string.ok, null).show();
            return;
        }

        onFolderChosen(dir);
    }

    /** Continue with a folder chosen by the user, asking for the permission when needed. */
    private void onFolderChosen(File dir) {
        if (StorageLocation.hasAllFilesAccess(this)) {
            changeTo(dir);
        } else {
            /* Continue in onResume, once the permission has been granted. */
            pending = dir;
            new AlertDialog.Builder(this)
                    .setMessage(R.string.storage_permission_explain)
                    .setPositiveButton(R.string.storage_grant, (d, w) -> requestAllFilesAccess())
                    .setNegativeButton(android.R.string.cancel, (d, w) -> pending = null)
                    .show();
        }
    }

    /**
     * Switch to another folder for the personal files.
     * @param dir The new folder, or null for the default one.
     */
    private void changeTo(File dir) {
        File from = StorageLocation.getEffectiveDir(this);
        File to = dir == null ? StorageLocation.getDefaultDir(this) : dir;
        if (from.getAbsolutePath().equals(to.getAbsolutePath())) {
            StorageLocation.setCustomDir(this, dir);
            updateViews();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.storage_title)
                .setMessage(getString(R.string.storage_copy_question, from.getAbsolutePath(), to.getAbsolutePath()))
                .setPositiveButton(R.string.storage_copy, (d, w) -> copyAndApply(from, to, dir))
                .setNeutralButton(R.string.storage_no_copy, (d, w) -> apply(dir, -1))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private void copyAndApply(File from, File to, File dir) {
        ProgressDialog progress = ProgressDialog.show(this, getString(R.string.storage_title), getString(R.string.storage_copying), true, false);
        new Thread(() -> {
            try {
                int copied = StorageLocation.copyContents(from, to);
                runOnUiThread(() -> {
                    progress.dismiss();
                    apply(dir, copied);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(this)
                            .setMessage(getString(R.string.storage_copy_failed, e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            }
        }, "openttd-copy").start();
    }

    private void apply(File dir, int copied) {
        StorageLocation.setCustomDir(this, dir);
        updateViews();

        String message = copied >= 0 ? getResources().getQuantityString(R.plurals.storage_copied, copied, copied) + "\n\n" : "";
        if (OpenTTDActivity.isRunning()) {
            /* The game only reads the location when it starts. */
            new AlertDialog.Builder(this)
                    .setTitle(R.string.storage_title)
                    .setMessage(message + getString(R.string.storage_restart))
                    .setPositiveButton(R.string.storage_restart_now, (d, w) -> restart())
                    .setNegativeButton(R.string.storage_restart_later, null)
                    .show();
        } else if (!message.isEmpty()) {
            new AlertDialog.Builder(this).setMessage(message.trim()).setPositiveButton(android.R.string.ok, null).show();
        }
    }

    /** Restart the app, so the game picks up the new location. */
    private void restart() {
        Intent intent = new Intent(this, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        Process.killProcess(Process.myPid());
    }
}

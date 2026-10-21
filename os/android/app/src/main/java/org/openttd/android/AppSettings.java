/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * The settings of the Android app (where the personal files are stored), shown
 * as a full screen dialog over the game, or in StorageActivity when opened
 * through the launcher shortcut.
 */
final class AppSettings {
    /** Request code for the folder picker; the host forwards the result to #onActivityResult. */
    static final int REQUEST_PICK_FOLDER = 0x0771;

    /** What the screen showing the settings provides. */
    interface Host {
        /** Whether a game is running that has to be restarted for changes to apply. */
        boolean isGameRunning();
        /** Restart the game, so it uses the new storage location. */
        void restartGame();
        /** Close the settings. */
        void close();
        /** Whether the on-screen controls can be edited, i.e. they are shown over the game. */
        default boolean canEditControls() { return false; }
        /** Close the settings and show the editor of the on-screen controls. */
        default void editControls() {}
    }

    private final Activity activity;
    private final Host host;
    /** Context to create the views with, so they follow the theme of the screen they are shown on. */
    private Context ui;
    private TextView current;
    private TextView status;
    private Button grant;
    /** Folder picked by the user, waiting for the permission to be granted. */
    private File pending;

    AppSettings(Activity activity, Host host) {
        this.activity = activity;
        this.host = host;
        this.ui = activity;
    }

    private String str(int id, Object... args) {
        return activity.getString(id, args);
    }

    /** Create the view with all settings, themed like the activity. */
    View createView() {
        return createView(activity);
    }

    /**
     * Create the view with all settings.
     * @param ui Context with the theme of the window the view is shown in.
     */
    View createView(Context ui) {
        this.ui = ui;
        int pad = (int)(24 * activity.getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(ui);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad / 2, pad, pad);

        /* Header with title and close button. */
        FrameLayout header = new FrameLayout(ui);
        TextView title = new TextView(ui);
        title.setText(R.string.app_settings_title);
        title.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Large);
        header.addView(title, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL));
        ImageButton close = new ImageButton(ui);
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        close.setBackground(null);
        close.setContentDescription(str(R.string.app_settings_close));
        close.setOnClickListener(v -> host.close());
        header.addView(close, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
        layout.addView(header);

        /* Controls. */
        TextView controls = new TextView(ui);
        controls.setText(R.string.controls_title);
        controls.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        controls.setPadding(0, pad, 0, pad / 2);
        layout.addView(controls);

        TextView controls_intro = new TextView(ui);
        controls_intro.setText(host.canEditControls() ? R.string.controls_intro : R.string.controls_in_game);
        layout.addView(controls_intro);

        if (host.canEditControls()) {
            Button edit = new Button(ui);
            edit.setText(R.string.controls_edit);
            edit.setOnClickListener(v -> host.editControls());
            layout.addView(edit);
        }

        TextView section = new TextView(ui);
        section.setText(R.string.storage_title);
        section.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        section.setPadding(0, pad, 0, pad / 2);
        layout.addView(section);

        TextView intro = new TextView(ui);
        intro.setText(R.string.storage_intro);
        layout.addView(intro);

        current = new TextView(ui);
        current.setPadding(0, pad, 0, pad);
        current.setTextIsSelectable(true);
        layout.addView(current);

        Button use_public = new Button(ui);
        use_public.setText(str(R.string.storage_use_public, StorageLocation.getPublicDir().getAbsolutePath()));
        use_public.setOnClickListener(v -> onFolderChosen(StorageLocation.getPublicDir()));
        layout.addView(use_public);

        Button choose = new Button(ui);
        choose.setText(R.string.storage_choose);
        choose.setOnClickListener(v -> activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQUEST_PICK_FOLDER));
        layout.addView(choose);

        Button reset = new Button(ui);
        reset.setText(R.string.storage_reset);
        reset.setOnClickListener(v -> changeTo(null));
        layout.addView(reset);

        status = new TextView(ui);
        status.setPadding(0, pad, 0, 0);
        layout.addView(status);

        grant = new Button(ui);
        grant.setText(R.string.storage_grant);
        grant.setOnClickListener(v -> StorageLocation.requestAllFilesAccess(activity));
        layout.addView(grant);

        ScrollView scroll = new ScrollView(ui);
        scroll.addView(layout);
        /* Keep the content clear of the system bars and display cutouts. */
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsets.CONSUMED;
        });

        updateViews();
        return scroll;
    }

    /** To be called when the host is resumed, e.g. after returning from the permission screen. */
    void onResume() {
        if (current == null) return;
        if (pending != null && StorageLocation.hasAllFilesAccess(activity)) {
            File dir = pending;
            pending = null;
            changeTo(dir);
        }
        updateViews();
    }

    /** To be called by the host with activity results; returns whether it was handled. */
    boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK_FOLDER) return false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return true;

        File dir = StorageLocation.treeUriToDir(data.getData());
        if (dir == null) {
            new AlertDialog.Builder(ui).setMessage(R.string.storage_unsupported).setPositiveButton(android.R.string.ok, null).show();
        } else {
            onFolderChosen(dir);
        }
        return true;
    }

    private void updateViews() {
        File custom = StorageLocation.getCustomDir(activity);
        File effective = StorageLocation.getEffectiveDir(activity);
        current.setText(str(R.string.storage_current, effective.getAbsolutePath()));

        boolean missing_permission = custom != null && !StorageLocation.hasAllFilesAccess(activity);
        status.setText(missing_permission ? str(R.string.storage_permission_missing, custom.getAbsolutePath()) : "");
        grant.setVisibility(missing_permission ? View.VISIBLE : View.GONE);
    }

    /** Continue with a folder chosen by the user, asking for the permission when needed. */
    private void onFolderChosen(File dir) {
        if (StorageLocation.hasAllFilesAccess(activity)) {
            changeTo(dir);
            return;
        }

        /* Continue in onResume, once the permission has been granted. */
        pending = dir;
        new AlertDialog.Builder(ui)
                .setMessage(R.string.storage_permission_explain)
                .setPositiveButton(R.string.storage_grant, (d, w) -> StorageLocation.requestAllFilesAccess(activity))
                .setNegativeButton(android.R.string.cancel, (d, w) -> pending = null)
                .show();
    }

    /**
     * Switch to another folder for the personal files.
     * @param dir The new folder, or null for the default one.
     */
    private void changeTo(File dir) {
        File from = StorageLocation.getEffectiveDir(activity);
        File to = dir == null ? StorageLocation.getDefaultDir(activity) : dir;
        if (from.getAbsolutePath().equals(to.getAbsolutePath())) {
            StorageLocation.setCustomDir(activity, dir);
            updateViews();
            return;
        }

        new AlertDialog.Builder(ui)
                .setTitle(R.string.storage_title)
                .setMessage(str(R.string.storage_copy_question, from.getAbsolutePath(), to.getAbsolutePath()))
                .setPositiveButton(R.string.storage_copy, (d, w) -> apply(dir, from))
                .setNeutralButton(R.string.storage_no_copy, (d, w) -> apply(dir, null))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Store the new location. The files are copied by the launcher the next time
     * the game starts, after the running game has written its latest files.
     */
    private void apply(File dir, File copy_from) {
        StorageLocation.setCustomDir(activity, dir);
        StorageLocation.setPendingCopy(activity, copy_from);
        updateViews();

        if (host.isGameRunning()) {
            new AlertDialog.Builder(ui)
                    .setTitle(R.string.storage_title)
                    .setMessage(R.string.storage_restart)
                    .setPositiveButton(R.string.storage_restart_now, (d, w) -> host.restartGame())
                    .setNegativeButton(R.string.storage_restart_later, null)
                    .show();
        } else if (copy_from != null) {
            new AlertDialog.Builder(ui).setMessage(R.string.storage_copy_on_start).setPositiveButton(android.R.string.ok, null).show();
        }
    }
}

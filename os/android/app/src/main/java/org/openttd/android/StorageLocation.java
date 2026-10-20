/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Where OpenTTD keeps its personal files (configuration, savegames,
 * scenarios, downloaded content, ...).
 *
 * By default that is the app-specific folder on the shared storage
 * (Android/data/.../files/openttd). Users can choose any other folder; as
 * OpenTTD uses plain file I/O, that needs the "All files access" permission.
 */
final class StorageLocation {
    private static final String TAG = "OpenTTD";
    private static final String PREFS = "storage";
    private static final String KEY_CUSTOM_DIR = "custom_dir";
    private static final String KEY_ASKED = "asked";
    /** Name of the personal folder within the XDG data/config homes; see PERSONAL_DIR in the CMake options. */
    private static final String PERSONAL_DIR = "openttd";

    private StorageLocation() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The default folder for the personal files. */
    static File getDefaultDir(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        return new File(base, PERSONAL_DIR);
    }

    /** The suggested folder outside of the app folder: "OpenTTD" in the root of the device storage. */
    static File getPublicDir() {
        return new File(Environment.getExternalStorageDirectory(), "OpenTTD");
    }

    /** Whether the user was asked where to store the files (or chose a folder already). */
    static boolean wasAsked(Context context) {
        return prefs(context).getBoolean(KEY_ASKED, false) || getCustomDir(context) != null;
    }

    static void setAsked(Context context) {
        prefs(context).edit().putBoolean(KEY_ASKED, true).commit();
    }

    /** The folder chosen by the user, or null when the default is used. */
    static File getCustomDir(Context context) {
        String dir = prefs(context).getString(KEY_CUSTOM_DIR, null);
        return dir == null ? null : new File(dir);
    }

    static void setCustomDir(Context context, File dir) {
        prefs(context).edit().putString(KEY_CUSTOM_DIR, dir == null ? null : dir.getAbsolutePath()).commit();
    }

    /** The folder that will actually be used, taking the permission into account. */
    static File getEffectiveDir(Context context) {
        File custom = getCustomDir(context);
        return custom != null && hasAllFilesAccess(context) ? custom : getDefaultDir(context);
    }

    /** Whether the app may use plain file I/O on all of the shared storage. */
    static boolean hasAllFilesAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    /** Ask the user for "All files access"; the activity is resumed afterwards. */
    static void requestAllFilesAccess(android.app.Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())));
        } else {
            activity.requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 0);
        }
    }

    /**
     * Convert a folder picked with ACTION_OPEN_DOCUMENT_TREE into a file system path.
     * @return The path, or null when the folder is not on the device storage or an SD card.
     */
    static File treeUriToDir(Uri uri) {
        if (!"com.android.externalstorage.documents".equals(uri.getAuthority())) return null;
        String id = DocumentsContract.getTreeDocumentId(uri); // e.g. "primary:Documents/OpenTTD"
        int colon = id.indexOf(':');
        if (colon < 0) return null;
        String volume = id.substring(0, colon);
        String path = id.substring(colon + 1);
        File base = "primary".equals(volume) ? Environment.getExternalStorageDirectory() : new File("/storage", volume);
        return path.isEmpty() ? base : new File(base, path);
    }

    /**
     * Prepare the environment for the native code; called before the game starts.
     * The native code (android_main.cpp) uses $OPENTTD_HOME as XDG data and config
     * home when set, so the personal files end up in $OPENTTD_HOME/openttd. For a
     * custom folder that is a symbolic link in the internal storage to that folder.
     */
    static void applyForNative(Context context) {
        try {
            File custom = getCustomDir(context);
            if (custom != null && hasAllFilesAccess(context) && (custom.isDirectory() || custom.mkdirs())) {
                File home = new File(context.getFilesDir(), "home");
                home.mkdirs();
                File link = new File(home, PERSONAL_DIR);
                link.delete();
                Os.symlink(custom.getAbsolutePath(), link.getAbsolutePath());
                Os.setenv("OPENTTD_HOME", home.getAbsolutePath(), true);
                return;
            }
            if (custom != null) Log.w(TAG, "Cannot use " + custom + "; falling back to the default storage location");
            Os.unsetenv("OPENTTD_HOME");
        } catch (ErrnoException e) {
            Log.e(TAG, "Cannot set up the storage location", e);
        }
    }

    /**
     * Copy all files from one folder into another; existing files are kept.
     * @return Number of files copied.
     */
    static int copyContents(File from, File to) throws IOException {
        File[] children = from.listFiles();
        if (children == null) return 0;
        if (!to.isDirectory() && !to.mkdirs()) throw new IOException("Cannot create " + to);

        int copied = 0;
        byte[] buffer = new byte[256 * 1024];
        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) {
                copied += copyContents(child, target);
            } else if (!target.exists()) {
                try (InputStream in = new FileInputStream(child); OutputStream out = new FileOutputStream(target)) {
                    int n;
                    while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                }
                target.setLastModified(child.lastModified());
                copied++;
            }
        }
        return copied;
    }
}

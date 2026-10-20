/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenTTD reads its data (base sets, languages, scripts) with plain file I/O,
 * so the files packaged in the APK assets are extracted to internal storage.
 * This is done once per installed APK version.
 */
final class DataInstaller {
    /** Folder inside the APK assets containing the game data. */
    private static final String ASSET_ROOT = "openttd";
    /** File in the data directory recording which APK the data came from. */
    private static final String STAMP_FILE = ".installed";

    interface Progress {
        void update(int done, int total);
    }

    private DataInstaller() {}

    /** Directory the native code uses as its working directory; see android_main.cpp. */
    static File getDataDir(Context context) {
        return new File(context.getFilesDir(), "data");
    }

    private static String getStamp(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.getLongVersionCode() + ":" + info.lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return "unknown";
        }
    }

    static boolean isInstalled(Context context) {
        File stamp = new File(getDataDir(context), STAMP_FILE);
        try {
            return stamp.exists() && new String(Files.readAllBytes(stamp.toPath()), StandardCharsets.UTF_8).equals(getStamp(context));
        } catch (IOException e) {
            return false;
        }
    }

    private static void collect(AssetManager assets, String path, List<String> out) throws IOException {
        String[] children = assets.list(path);
        if (children == null || children.length == 0) {
            out.add(path);
            return;
        }
        for (String child : children) collect(assets, path + "/" + child, out);
    }

    private static void deleteRecursive(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }

    static void install(Context context, Progress progress) throws IOException {
        AssetManager assets = context.getAssets();
        File dataDir = getDataDir(context);
        deleteRecursive(dataDir);

        List<String> files = new ArrayList<>();
        collect(assets, ASSET_ROOT, files);

        byte[] buffer = new byte[256 * 1024];
        int done = 0;
        for (String asset : files) {
            File target = new File(dataDir, asset.substring(ASSET_ROOT.length() + 1));
            target.getParentFile().mkdirs();
            try (InputStream in = assets.open(asset); OutputStream out = new FileOutputStream(target)) {
                int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            }
            progress.update(++done, files.size());
        }

        Files.write(new File(dataDir, STAMP_FILE).toPath(), getStamp(context).getBytes(StandardCharsets.UTF_8));
    }
}

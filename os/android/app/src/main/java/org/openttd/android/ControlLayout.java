/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

package org.openttd.android;

import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The layout of the on-screen controls, edited with LayoutEditor. It is kept
 * as a readable JSON file next to the savegames, so it moves along with them.
 */
final class ControlLayout {
    private static final String TAG = "OpenTTD";
    private static final String FILE_NAME = "android_controls.json";
    private static final int FORMAT_VERSION = 1;

    /** Smallest and largest size of a control, in dp. */
    static final int MIN_SIZE = 40, MAX_SIZE = 240;

    enum Type { JOYSTICK, MOUSE_LEFT, MOUSE_RIGHT, KEY }

    /** One on-screen control. */
    static final class Element {
        Type type;
        /** Centre of the control, as a fraction of the screen width and height. */
        float x, y;
        /** Width and height, in dp. */
        int size;
        /** For KEY: the text shown, the Android key code and the modifiers held with it. */
        String label = "";
        int key = KeyEvent.KEYCODE_UNKNOWN;
        boolean ctrl, shift, alt;

        Element(Type type, float x, float y, int size) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        Element copy() {
            Element e = new Element(type, x, y, size);
            e.label = label;
            e.key = key;
            e.ctrl = ctrl;
            e.shift = shift;
            e.alt = alt;
            return e;
        }

        /** The text shown on the control. */
        String displayLabel() {
            switch (type) {
                case MOUSE_LEFT: return "L";
                case MOUSE_RIGHT: return "R";
                case KEY: return label.isEmpty() ? keyName(key) : label;
                default: return "";
            }
        }
    }

    /** A ready-made key button, using OpenTTD's default hotkeys. */
    static final class Preset {
        final String name, label;
        final int key;
        final boolean ctrl, shift;

        Preset(String name, String label, int key, boolean ctrl, boolean shift) {
            this.name = name;
            this.label = label;
            this.key = key;
            this.ctrl = ctrl;
            this.shift = shift;
        }

        Element create(float x, float y) {
            Element e = new Element(Type.KEY, x, y, 64);
            e.label = label;
            e.key = key;
            e.ctrl = ctrl;
            e.shift = shift;
            return e;
        }
    }

    static final Preset[] PRESETS = {
        new Preset("Hold Ctrl", "Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, false, false),
        new Preset("Hold Shift", "Shift", KeyEvent.KEYCODE_SHIFT_LEFT, false, false),
        new Preset("Zoom in", "+", KeyEvent.KEYCODE_EQUALS, false, false),
        new Preset("Zoom out", "-", KeyEvent.KEYCODE_MINUS, false, false),
        new Preset("Pause", "||", KeyEvent.KEYCODE_F1, false, false),
        new Preset("Close window", "✕", KeyEvent.KEYCODE_FORWARD_DEL, false, false),
        new Preset("Close all windows", "✕✕", KeyEvent.KEYCODE_FORWARD_DEL, false, true),
        new Preset("Cancel tool", "Esc", KeyEvent.KEYCODE_ESCAPE, false, false),
        new Preset("Map", "Map", KeyEvent.KEYCODE_M, false, false),
        new Preset("Build railway", "Rail", KeyEvent.KEYCODE_F7, false, true),
        new Preset("Build road", "Road", KeyEvent.KEYCODE_F8, false, true),
        new Preset("Landscaping", "Land", KeyEvent.KEYCODE_L, false, false),
        new Preset("Centre view", "C", KeyEvent.KEYCODE_C, false, false),
    };

    /** Keys a custom key button can send. */
    static final int[] KEYS;
    static {
        List<Integer> keys = new ArrayList<>();
        for (int k = KeyEvent.KEYCODE_A; k <= KeyEvent.KEYCODE_Z; k++) keys.add(k);
        for (int k = KeyEvent.KEYCODE_0; k <= KeyEvent.KEYCODE_9; k++) keys.add(k);
        for (int k = KeyEvent.KEYCODE_F1; k <= KeyEvent.KEYCODE_F12; k++) keys.add(k);
        int[] other = {
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_TAB,
            KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_INSERT,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_GRAVE,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_ALT_LEFT,
        };
        for (int k : other) keys.add(k);
        KEYS = new int[keys.size()];
        for (int i = 0; i < KEYS.length; i++) KEYS[i] = keys.get(i);
    }

    /** Name of a key, as shown to the user. */
    static String keyName(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_ESCAPE: return "Esc";
            case KeyEvent.KEYCODE_SPACE: return "Space";
            case KeyEvent.KEYCODE_ENTER: return "Enter";
            case KeyEvent.KEYCODE_TAB: return "Tab";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "Delete";
            case KeyEvent.KEYCODE_DEL: return "Backspace";
            case KeyEvent.KEYCODE_INSERT: return "Insert";
            case KeyEvent.KEYCODE_MOVE_HOME: return "Home";
            case KeyEvent.KEYCODE_MOVE_END: return "End";
            case KeyEvent.KEYCODE_PAGE_UP: return "Page up";
            case KeyEvent.KEYCODE_PAGE_DOWN: return "Page down";
            case KeyEvent.KEYCODE_DPAD_UP: return "↑";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "↓";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "←";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "→";
            case KeyEvent.KEYCODE_EQUALS: return "=";
            case KeyEvent.KEYCODE_MINUS: return "-";
            case KeyEvent.KEYCODE_GRAVE: return "`";
            case KeyEvent.KEYCODE_CTRL_LEFT: return "Ctrl";
            case KeyEvent.KEYCODE_SHIFT_LEFT: return "Shift";
            case KeyEvent.KEYCODE_ALT_LEFT: return "Alt";
            default: {
                String name = KeyEvent.keyCodeToString(key);
                return name.startsWith("KEYCODE_") ? name.substring(8) : name;
            }
        }
    }

    final List<Element> elements = new ArrayList<>();

    ControlLayout copy() {
        ControlLayout l = new ControlLayout();
        for (Element e : elements) l.elements.add(e.copy());
        return l;
    }

    boolean hasJoystick() {
        for (Element e : elements) if (e.type == Type.JOYSTICK) return true;
        return false;
    }

    static File getFile(Context context) {
        return new File(StorageLocation.getEffectiveDir(context), FILE_NAME);
    }

    /**
     * The default layout: joystick in a bottom corner with the mouse buttons above it.
     * @param width Width of the screen, in px.
     * @param height Height of the screen, in px.
     * @param on_left Whether the controls are in the bottom left corner.
     * @param joystick Whether to add the joystick.
     * @param buttons Whether to add the mouse buttons.
     */
    static ControlLayout createDefault(Context context, int width, int height, boolean on_left, boolean joystick, boolean buttons) {
        float density = context.getResources().getDisplayMetrics().density;
        float margin = 24 * density;
        float gap = 12 * density;
        float joy = 140 * density;
        float button = 64 * density;

        ControlLayout layout = new ControlLayout();
        float cx = on_left ? margin + joy / 2 : width - margin - joy / 2;
        float cy = height - margin - joy / 2;
        if (joystick) layout.elements.add(new Element(Type.JOYSTICK, cx / width, cy / height, 140));
        if (buttons) {
            /* Side by side above the joystick, or stacked in the corner without it. */
            float bx_left, bx_right, by_left, by_right;
            if (joystick) {
                bx_left = cx - joy / 2 + button / 2;
                bx_right = cx + joy / 2 - button / 2;
                by_left = by_right = cy - joy / 2 - gap - button / 2;
            } else {
                bx_left = bx_right = on_left ? margin + button / 2 : width - margin - button / 2;
                by_right = height - margin - button / 2;
                by_left = by_right - button - gap;
            }
            layout.elements.add(new Element(Type.MOUSE_LEFT, bx_left / width, by_left / height, 64));
            layout.elements.add(new Element(Type.MOUSE_RIGHT, bx_right / width, by_right / height, 64));
        }
        return layout;
    }

    /**
     * Load the layout of the user, or make one from the settings of older
     * versions of the app when there is none yet.
     */
    static ControlLayout load(Context context, int width, int height) {
        File file = getFile(context);
        if (file.isFile()) {
            try {
                return fromJson(new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
            } catch (IOException | JSONException | IllegalArgumentException e) {
                Log.e(TAG, "Cannot read " + file + "; using the default controls", e);
            }
        }
        return createDefault(context, width, height, Controls.getSide(context) == Controls.Side.LEFT, Controls.getJoystick(context), Controls.getClickButtons(context));
    }

    void save(Context context) {
        File file = getFile(context);
        File tmp = new File(file.getPath() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(file)) throw new IOException("Cannot rename " + tmp + " to " + file);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Cannot save the controls to " + file, e);
            tmp.delete();
        }
    }

    private JSONObject toJson() throws JSONException {
        JSONArray controls = new JSONArray();
        for (Element e : elements) {
            JSONObject o = new JSONObject();
            o.put("type", e.type.name().toLowerCase());
            o.put("x", (double)e.x);
            o.put("y", (double)e.y);
            o.put("size", e.size);
            if (e.type == Type.KEY) {
                o.put("label", e.label);
                o.put("key", KeyEvent.keyCodeToString(e.key).replaceFirst("^KEYCODE_", ""));
                if (e.ctrl) o.put("ctrl", true);
                if (e.shift) o.put("shift", true);
                if (e.alt) o.put("alt", true);
            }
            controls.put(o);
        }
        JSONObject root = new JSONObject();
        root.put("version", FORMAT_VERSION);
        root.put("controls", controls);
        return root;
    }

    private static ControlLayout fromJson(JSONObject root) throws JSONException {
        ControlLayout layout = new ControlLayout();
        JSONArray controls = root.getJSONArray("controls");
        for (int i = 0; i < controls.length(); i++) {
            JSONObject o = controls.getJSONObject(i);
            Type type;
            try {
                type = Type.valueOf(o.getString("type").toUpperCase());
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Skipping unknown control " + o.optString("type"));
                continue;
            }
            float x = clamp((float)o.getDouble("x"), 0, 1);
            float y = clamp((float)o.getDouble("y"), 0, 1);
            int size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, o.optInt("size", 64)));
            Element e = new Element(type, x, y, size);
            if (type == Type.KEY) {
                e.label = o.optString("label", "");
                e.key = KeyEvent.keyCodeFromString("KEYCODE_" + o.optString("key", "UNKNOWN"));
                e.ctrl = o.optBoolean("ctrl", false);
                e.shift = o.optBoolean("shift", false);
                e.alt = o.optBoolean("alt", false);
                if (e.key == KeyEvent.KEYCODE_UNKNOWN) continue;
            }
            layout.elements.add(e);
        }
        return layout;
    }

    static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}

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
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The on-screen controls over the game, following the ControlLayout of the
 * user, and the editor to change that layout. Each control is a separate view
 * in the game's layout, so touches next to the controls still reach the game.
 */
final class OnScreenControls {
    private final Activity activity;
    private final RelativeLayout parent;
    /** Context for the dialogs, following the system's day/night theme. */
    private final Context ui;
    private final float density;

    /** The layout shown; loaded once the size of the screen is known. */
    private ControlLayout layout;
    private final List<View> views = new ArrayList<>();
    /** Size of the parent the controls were placed for. */
    private int placed_width, placed_height;

    /** The editor, while editing; with the layout from before editing, to cancel. */
    private Editor editor;
    private ControlLayout before_editing;

    OnScreenControls(Activity activity, RelativeLayout parent) {
        this.activity = activity;
        this.parent = parent;
        this.ui = new ContextThemeWrapper(activity, R.style.StorageTheme);
        this.density = activity.getResources().getDisplayMetrics().density;

        /* Follow changes of the screen size, e.g. when folding or rotating. */
        parent.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != placed_width || b - t != placed_height) parent.post(this::refresh);
        });
    }

    /** Show the controls of the stored layout, e.g. after the storage location changed. */
    void reload() {
        if (editor != null) return;
        layout = null;
        refresh();
    }

    /** Create and place the views for all controls. */
    void refresh() {
        int width = parent.getWidth(), height = parent.getHeight();
        if (width == 0 || height == 0) return; // Placed by the layout change listener later.
        if (layout == null) layout = ControlLayout.load(activity, width, height);

        for (View v : views) parent.removeView(v);
        views.clear();
        placed_width = width;
        placed_height = height;

        for (ControlLayout.Element e : layout.elements) {
            View v = createView(e);
            views.add(v);
            parent.addView(v, new RelativeLayout.LayoutParams(0, 0));
            place(v, e);
        }
        if (editor != null) editor.bringToFront();
    }

    private View createView(ControlLayout.Element e) {
        switch (e.type) {
            case JOYSTICK: return new JoystickView(activity);
            case MOUSE_LEFT: return new ClickButtonView(activity, e.displayLabel(), "Left click", ClickButtonView.mouse(0));
            case MOUSE_RIGHT: return new ClickButtonView(activity, e.displayLabel(), "Right click", ClickButtonView.mouse(1));
            default: return new ClickButtonView(activity, e.displayLabel(), e.displayLabel(), ClickButtonView.key(e.key, e.ctrl, e.shift, e.alt));
        }
    }

    /** Put the view of a control at its place, keeping it fully on the screen. */
    private void place(View v, ControlLayout.Element e) {
        int size = Math.round(e.size * density);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)v.getLayoutParams();
        params.width = size;
        params.height = size;
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.leftMargin = Math.max(0, Math.min(placed_width - size, Math.round(e.x * placed_width - size / 2.0f)));
        params.topMargin = Math.max(0, Math.min(placed_height - size, Math.round(e.y * placed_height - size / 2.0f)));
        v.setLayoutParams(params);
    }

    boolean isEditing() {
        return editor != null;
    }

    /** Show the editor over the game; the game gets no touches meanwhile. */
    void startEditing() {
        if (editor != null) return;
        if (layout == null) refresh();
        if (layout == null) return;
        before_editing = layout.copy();
        editor = new Editor(activity);
        parent.addView(editor, new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
    }

    /** Close the editor, keeping and storing the new layout or going back to the old one. */
    void stopEditing(boolean keep) {
        if (editor == null) return;
        parent.removeView(editor);
        editor = null;
        if (keep) {
            layout.save(activity);
        } else {
            layout = before_editing;
        }
        before_editing = null;
        refresh();
    }

    private String str(int id, Object... args) {
        return activity.getString(id, args);
    }

    /** Add a new control in the middle of the screen. */
    private void showAddMenu() {
        List<String> names = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (!layout.hasJoystick()) {
            names.add(str(R.string.controls_joystick_name));
            actions.add(() -> add(new ControlLayout.Element(ControlLayout.Type.JOYSTICK, 0.5f, 0.5f, 140)));
        }
        names.add(str(R.string.controls_left));
        actions.add(() -> add(new ControlLayout.Element(ControlLayout.Type.MOUSE_LEFT, 0.5f, 0.5f, 64)));
        names.add(str(R.string.controls_right));
        actions.add(() -> add(new ControlLayout.Element(ControlLayout.Type.MOUSE_RIGHT, 0.5f, 0.5f, 64)));
        for (ControlLayout.Preset p : ControlLayout.PRESETS) {
            names.add(p.name + " (" + p.label + ")");
            actions.add(() -> add(p.create(0.5f, 0.5f)));
        }
        names.add(str(R.string.controls_custom_key));
        actions.add(() -> {
            ControlLayout.Element e = new ControlLayout.Element(ControlLayout.Type.KEY, 0.5f, 0.5f, 64);
            e.key = ControlLayout.KEYS[0];
            showProperties(e, true);
        });

        new AlertDialog.Builder(ui)
                .setTitle(R.string.controls_add)
                .setItems(names.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void add(ControlLayout.Element e) {
        layout.elements.add(e);
        refresh();
    }

    /**
     * Change the size and, for keys, what it does; or remove the control.
     * @param is_new Whether the control is not part of the layout yet.
     */
    private void showProperties(ControlLayout.Element e, boolean is_new) {
        int pad = (int)(24 * density);
        LinearLayout content = new LinearLayout(ui);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, 0);

        TextView size_label = new TextView(ui);
        size_label.setText(str(R.string.controls_size, e.size));
        content.addView(size_label);
        SeekBar size = new SeekBar(ui);
        size.setMax(ControlLayout.MAX_SIZE - ControlLayout.MIN_SIZE);
        size.setProgress(e.size - ControlLayout.MIN_SIZE);
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean from_user) {
                size_label.setText(str(R.string.controls_size, progress + ControlLayout.MIN_SIZE));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(size);

        EditText label = new EditText(ui);
        Spinner key = new Spinner(ui);
        CheckBox ctrl = new CheckBox(ui), shift = new CheckBox(ui), alt = new CheckBox(ui);
        boolean is_key = e.type == ControlLayout.Type.KEY;
        if (is_key) {
            TextView label_title = new TextView(ui);
            label_title.setText(R.string.controls_label);
            label_title.setPadding(0, pad / 2, 0, 0);
            content.addView(label_title);
            label.setInputType(InputType.TYPE_CLASS_TEXT);
            label.setSingleLine(true);
            label.setText(e.label);
            label.setHint(ControlLayout.keyName(e.key));
            content.addView(label);

            TextView key_title = new TextView(ui);
            key_title.setText(R.string.controls_key);
            key_title.setPadding(0, pad / 2, 0, 0);
            content.addView(key_title);
            String[] key_names = new String[ControlLayout.KEYS.length];
            int selected = 0;
            for (int i = 0; i < key_names.length; i++) {
                key_names[i] = ControlLayout.keyName(ControlLayout.KEYS[i]);
                if (ControlLayout.KEYS[i] == e.key) selected = i;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(ui, android.R.layout.simple_spinner_item, key_names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            key.setAdapter(adapter);
            key.setSelection(selected);
            content.addView(key);

            LinearLayout modifiers = new LinearLayout(ui);
            modifiers.setOrientation(LinearLayout.HORIZONTAL);
            ctrl.setText(R.string.controls_ctrl);
            ctrl.setChecked(e.ctrl);
            shift.setText(R.string.controls_shift);
            shift.setChecked(e.shift);
            alt.setText(R.string.controls_alt);
            alt.setChecked(e.alt);
            modifiers.addView(ctrl);
            modifiers.addView(shift);
            modifiers.addView(alt);
            content.addView(modifiers);
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(ui);
        scroll.addView(content);

        AlertDialog.Builder builder = new AlertDialog.Builder(ui)
                .setTitle(is_key ? str(R.string.controls_key_button) : e.type == ControlLayout.Type.JOYSTICK ? str(R.string.controls_joystick_name) : str(e.type == ControlLayout.Type.MOUSE_LEFT ? R.string.controls_left : R.string.controls_right))
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    e.size = size.getProgress() + ControlLayout.MIN_SIZE;
                    if (is_key) {
                        e.label = label.getText().toString().trim();
                        e.key = ControlLayout.KEYS[key.getSelectedItemPosition()];
                        e.ctrl = ctrl.isChecked();
                        e.shift = shift.isChecked();
                        e.alt = alt.isChecked();
                    }
                    if (is_new) layout.elements.add(e);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (!is_new) {
            builder.setNeutralButton(R.string.controls_remove, (d, w) -> {
                layout.elements.remove(e);
                refresh();
            });
        }
        builder.show();
    }

    private void confirmReset() {
        new AlertDialog.Builder(ui)
                .setMessage(R.string.controls_reset_question)
                .setPositiveButton(R.string.controls_reset, (d, w) -> {
                    layout = ControlLayout.createDefault(activity, placed_width, placed_height, Controls.getSide(activity) == Controls.Side.LEFT, true, true);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Full screen view over the game and the controls, to move them around. */
    private final class Editor extends FrameLayout {
        private final int touch_slop;
        /** The control being touched, and where it was touched relative to its centre. */
        private ControlLayout.Element touched;
        private View touched_view;
        private float offset_x, offset_y, down_x, down_y;
        private boolean dragging;

        Editor(Context context) {
            super(context);
            touch_slop = ViewConfiguration.get(context).getScaledTouchSlop();
            setBackgroundColor(0x60000000);

            LinearLayout bar = new LinearLayout(context);
            bar.setOrientation(LinearLayout.VERTICAL);
            bar.setGravity(Gravity.CENTER_HORIZONTAL);
            int pad = (int)(8 * density);
            bar.setPadding(pad, pad, pad, pad);

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            buttons.addView(button(context, R.string.controls_add, v -> showAddMenu()));
            buttons.addView(button(context, R.string.controls_reset, v -> confirmReset()));
            buttons.addView(button(context, android.R.string.cancel, v -> stopEditing(false)));
            buttons.addView(button(context, R.string.controls_done, v -> stopEditing(true)));
            bar.addView(buttons);

            TextView hint = new TextView(context);
            hint.setText(R.string.controls_edit_hint);
            hint.setTextColor(0xFFFFFFFF);
            hint.setShadowLayer(4 * density, 0, 0, 0xFF000000);
            hint.setGravity(Gravity.CENTER_HORIZONTAL);
            bar.addView(hint);

            addView(bar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        }

        private Button button(Context context, int text, View.OnClickListener listener) {
            Button b = new Button(context);
            b.setText(text);
            b.setOnClickListener(listener);
            return b;
        }

        /** The control at a position, the one drawn on top first. */
        private int find(float x, float y) {
            for (int i = views.size() - 1; i >= 0; i--) {
                View v = views.get(i);
                if (x >= v.getLeft() && x < v.getRight() && y >= v.getTop() && y < v.getBottom()) return i;
            }
            return -1;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    int i = find(x, y);
                    touched = i < 0 ? null : layout.elements.get(i);
                    touched_view = i < 0 ? null : views.get(i);
                    dragging = false;
                    down_x = x;
                    down_y = y;
                    if (touched_view != null) {
                        offset_x = (touched_view.getLeft() + touched_view.getRight()) / 2.0f - x;
                        offset_y = (touched_view.getTop() + touched_view.getBottom()) / 2.0f - y;
                    }
                    break;
                }

                case MotionEvent.ACTION_MOVE:
                    if (touched == null) break;
                    if (!dragging && Math.hypot(x - down_x, y - down_y) > touch_slop) dragging = true;
                    if (dragging) {
                        touched.x = ControlLayout.clamp((x + offset_x) / placed_width, 0, 1);
                        touched.y = ControlLayout.clamp((y + offset_y) / placed_height, 0, 1);
                        place(touched_view, touched);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    if (touched != null && !dragging) showProperties(touched, false);
                    touched = null;
                    touched_view = null;
                    break;

                case MotionEvent.ACTION_CANCEL:
                    touched = null;
                    touched_view = null;
                    break;
            }
            /* Keep all touches away from the game while editing. */
            return true;
        }
    }
}

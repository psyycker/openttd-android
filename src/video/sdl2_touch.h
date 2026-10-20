/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

/** @file sdl2_touch.h Touch screen used as a trackpad, for the SDL2 video driver. */

#ifndef VIDEO_SDL2_TOUCH_H
#define VIDEO_SDL2_TOUCH_H

#include <SDL.h>

/**
 * Turns a touch screen into a laptop-style trackpad driving the mouse cursor,
 * so the desktop interface can be used as-is:
 * - one finger moves the cursor;
 * - tap is a left click, double tap a double click;
 * - tap, then touch again and move, drags with the left button held;
 * - two finger tap is a right click;
 * - two finger drag scrolls the map or the list under the cursor;
 * - pinch zooms the viewport under the cursor;
 * - three finger tap toggles the on-screen keyboard.
 */
class TouchTrackpad {
public:
	void Initialize();
	bool HandleEvent(const SDL_Event &ev);
	void Tick();

private:
	using Clock = std::chrono::steady_clock;

	/** A finger currently touching the screen, in screen pixels. */
	struct Finger {
		SDL_FingerID id;
		float x, y;
	};

	/** What the fingers are doing in the current gesture. */
	enum class Gesture : uint8_t {
		None,      ///< No finger down.
		Pointer,   ///< One finger moving the cursor.
		Dragging,  ///< One finger moving the cursor with the left button held.
		TwoFinger, ///< Two finger scroll / pinch / tap.
		Other,     ///< Three or more fingers; only a tap is recognised.
	};

	std::vector<Finger> fingers;
	Gesture gesture = Gesture::None;
	size_t max_fingers = 0;           ///< Most fingers down at once during this gesture.
	Clock::time_point gesture_start;  ///< When the first finger of this gesture went down.
	float travel = 0;                 ///< Distance moved during this gesture, for tap detection.
	uint32_t last_motion_time = 0;    ///< SDL timestamp of the last one finger motion.

	bool tap_pending = false;         ///< A tap happened; click unless it turns into a double tap or drag.
	Clock::time_point tap_time;       ///< When the pending tap happened.
	bool drag_candidate = false;      ///< Finger went down right after a tap: a double tap or a drag.

	float last_cx = 0, last_cy = 0;   ///< Previous centre of the two fingers.
	float pinch_reference = 0;        ///< Finger distance at the last zoom step.
	float pan_rest_x = 0, pan_rest_y = 0; ///< Sub-pixel scroll remainder.

	float cursor_x = 0, cursor_y = 0; ///< Cursor position with sub-pixel precision.
	float density = 1;                ///< Screen density relative to 160 DPI, to scale thresholds.

	Finger *FindFinger(SDL_FingerID id);
	void CentreAndDistance(float &cx, float &cy, float &distance) const;
	void MoveCursor(float dx, float dy, uint32_t timestamp);
	void SetLeftButton(bool down);
	void LeftClick();
	void RightClick();
	void OnFingerDown(const SDL_TouchFingerEvent &ev);
	void OnFingerMotion(const SDL_TouchFingerEvent &ev);
	void OnFingerUp(const SDL_TouchFingerEvent &ev);
};

#endif /* VIDEO_SDL2_TOUCH_H */

/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

/** @file sdl2_touch.cpp Touch screen used as a trackpad, for the SDL2 video driver. */

#include "../stdafx.h"
#include "../gfx_func.h"
#include "../window_func.h"
#include "../core/math_func.hpp"
#include "sdl2_touch.h"

#include "../safeguards.h"

/** Longest a finger may be down to count as a tap. */
static constexpr auto TAP_MAX_DURATION = std::chrono::milliseconds(250);
/** After a tap, how long a new touch turns it into a double tap or drag instead of a click. */
static constexpr auto TAP_DRAG_WINDOW = std::chrono::milliseconds(180);
/** Furthest a finger may move (in dp) and still count as a tap. */
static constexpr float TAP_SLOP = 10.0f;
/** Change in finger distance needed for one zoom step. */
static constexpr float PINCH_STEP = 1.3f;
/** Distance two fingers scroll (in dp) for one mouse wheel step outside viewports. */
static constexpr float WHEEL_STEP = 24.0f;

void TouchTrackpad::Initialize()
{
	float ddpi;
	if (SDL_GetDisplayDPI(0, &ddpi, nullptr, nullptr) == 0 && ddpi > 0) this->density = ddpi / 160.0f;

	this->cursor_x = _cursor.pos.x;
	this->cursor_y = _cursor.pos.y;
}

TouchTrackpad::Finger *TouchTrackpad::FindFinger(SDL_FingerID id)
{
	for (Finger &f : this->fingers) {
		if (f.id == id) return &f;
	}
	return nullptr;
}

void TouchTrackpad::CentreAndDistance(float &cx, float &cy, float &distance) const
{
	const Finger &a = this->fingers[0];
	const Finger &b = this->fingers[1];
	cx = (a.x + b.x) / 2;
	cy = (a.y + b.y) / 2;
	distance = std::hypot(a.x - b.x, a.y - b.y);
}

/**
 * Move the cursor like a trackpad does: slow movements are precise, fast
 * movements cover more distance.
 */
void TouchTrackpad::MoveCursor(float dx, float dy, uint32_t timestamp)
{
	uint32_t dt = std::max<uint32_t>(1, timestamp - this->last_motion_time);
	this->last_motion_time = timestamp;

	float speed = std::hypot(dx, dy) / this->density / dt; // dp per millisecond
	float gain = std::min(1.0f + speed * 0.6f, 3.0f);

	this->cursor_x = Clamp(this->cursor_x + dx * gain, 0.0f, static_cast<float>(_screen.width - 1));
	this->cursor_y = Clamp(this->cursor_y + dy * gain, 0.0f, static_cast<float>(_screen.height - 1));

	int x = static_cast<int>(this->cursor_x);
	int y = static_cast<int>(this->cursor_y);
	if (_cursor.fix_at) {
		_cursor.UpdateCursorPositionRelative(x - _cursor.pos.x, y - _cursor.pos.y);
		this->cursor_x = _cursor.pos.x;
		this->cursor_y = _cursor.pos.y;
	} else {
		_cursor.UpdateCursorPosition(x, y);
	}
	HandleMouseEvents();
}

void TouchTrackpad::SetLeftButton(bool down)
{
	_left_button_down = down;
	if (!down) _left_button_clicked = false;
	HandleMouseEvents();
}

void TouchTrackpad::LeftClick()
{
	this->SetLeftButton(true);
	this->SetLeftButton(false);
}

void TouchTrackpad::RightClick()
{
	_right_button_down = true;
	_right_button_clicked = true;
	HandleMouseEvents();
	_right_button_down = false;
	HandleMouseEvents();
}

void TouchTrackpad::OnFingerDown(const SDL_TouchFingerEvent &ev)
{
	this->fingers.push_back({ev.fingerId, ev.x * _screen.width, ev.y * _screen.height});
	this->max_fingers = std::max(this->max_fingers, this->fingers.size());

	switch (this->fingers.size()) {
		case 1:
			this->gesture = Gesture::Pointer;
			this->gesture_start = Clock::now();
			this->travel = 0;
			this->max_fingers = 1;
			this->last_motion_time = ev.timestamp;
			/* A touch right after a tap may become a double tap or a drag. */
			this->drag_candidate = this->tap_pending && Clock::now() - this->tap_time < TAP_DRAG_WINDOW;
			this->tap_pending = false;
			break;

		case 2: {
			if (this->gesture == Gesture::Dragging) this->SetLeftButton(false);
			if (this->drag_candidate) {
				/* The earlier tap was a genuine click after all. */
				this->LeftClick();
				this->drag_candidate = false;
			}
			this->gesture = Gesture::TwoFinger;
			float distance;
			this->CentreAndDistance(this->last_cx, this->last_cy, distance);
			this->pinch_reference = distance;
			this->pan_rest_x = this->pan_rest_y = 0;
			break;
		}

		default:
			this->gesture = Gesture::Other;
			break;
	}
}

void TouchTrackpad::OnFingerMotion(const SDL_TouchFingerEvent &ev)
{
	Finger *f = this->FindFinger(ev.fingerId);
	if (f == nullptr) return;

	float dx = ev.x * _screen.width - f->x;
	float dy = ev.y * _screen.height - f->y;
	f->x += dx;
	f->y += dy;

	switch (this->gesture) {
		case Gesture::Pointer:
			this->travel += std::hypot(dx, dy);
			if (this->drag_candidate && this->travel > TAP_SLOP * this->density) {
				/* Tap followed by touch and move: drag with the left button held. */
				this->drag_candidate = false;
				this->gesture = Gesture::Dragging;
				this->SetLeftButton(true);
			}
			this->MoveCursor(dx, dy, ev.timestamp);
			break;

		case Gesture::Dragging:
			this->MoveCursor(dx, dy, ev.timestamp);
			break;

		case Gesture::TwoFinger: {
			if (this->fingers.size() != 2) break;

			float cx, cy, distance;
			this->CentreAndDistance(cx, cy, distance);
			float pan_x = cx - this->last_cx + this->pan_rest_x;
			float pan_y = cy - this->last_cy + this->pan_rest_y;
			this->travel += std::hypot(cx - this->last_cx, cy - this->last_cy);
			this->last_cx = cx;
			this->last_cy = cy;

			int ipan_x = static_cast<int>(pan_x);
			int ipan_y = static_cast<int>(pan_y);
			this->pan_rest_x = pan_x - ipan_x;
			this->pan_rest_y = pan_y - ipan_y;
			if (ipan_x != 0 || ipan_y != 0) {
				if (!HandleTouchPan({ipan_x, ipan_y})) {
					/* Not over a viewport: scroll the list under the cursor with the mouse wheel instead. */
					float step = WHEEL_STEP * this->density;
					this->pan_rest_x = 0;
					this->pan_rest_y = pan_y;
					if (std::abs(pan_y) >= step) {
						int wheel = static_cast<int>(-pan_y / step);
						this->pan_rest_y = pan_y + wheel * step;
						_cursor.wheel += wheel;
						HandleMouseEvents();
					}
				} else {
					this->pan_rest_x = this->pan_rest_y = 0;
				}
			}

			if (this->pinch_reference > 0) {
				float ratio = distance / this->pinch_reference;
				if (ratio > PINCH_STEP || ratio < 1.0f / PINCH_STEP) {
					HandleTouchZoom(ratio > 1.0f);
					this->pinch_reference = distance;
					this->travel += TAP_SLOP * this->density; // A pinch is never a tap.
				}
			}
			break;
		}

		default:
			break;
	}
}

void TouchTrackpad::OnFingerUp(const SDL_TouchFingerEvent &ev)
{
	auto it = std::ranges::find(this->fingers, ev.fingerId, &Finger::id);
	if (it == this->fingers.end()) return;
	this->fingers.erase(it);
	if (!this->fingers.empty()) {
		/* The gesture continues until all fingers are lifted; e.g. the last finger of a scroll does not move the cursor. */
		return;
	}

	bool is_tap = Clock::now() - this->gesture_start < TAP_MAX_DURATION && this->travel < TAP_SLOP * this->density;

	switch (this->gesture) {
		case Gesture::Pointer:
			if (is_tap && this->drag_candidate) {
				/* Double tap: both clicks now, so the double click is detected. */
				this->LeftClick();
				this->LeftClick();
			} else if (is_tap) {
				/* Wait a moment, this may be the start of a drag. */
				this->tap_pending = true;
				this->tap_time = Clock::now();
			} else if (this->drag_candidate) {
				/* Tap, then a touch that did not move enough to drag nor was short enough to be a tap. */
				this->LeftClick();
			}
			break;

		case Gesture::Dragging:
			this->SetLeftButton(false);
			break;

		case Gesture::TwoFinger:
			if (is_tap && this->max_fingers == 2) this->RightClick();
			break;

		case Gesture::Other:
			if (is_tap && this->max_fingers == 3) {
				if (SDL_IsTextInputActive()) {
					SDL_StopTextInput();
				} else {
					SDL_StartTextInput();
				}
			}
			break;

		default:
			break;
	}

	this->drag_candidate = false;
	this->gesture = Gesture::None;
}

/**
 * Handle an SDL event if it is a touch event.
 * @param ev The event.
 * @return Whether the event was handled.
 */
bool TouchTrackpad::HandleEvent(const SDL_Event &ev)
{
	switch (ev.type) {
		case SDL_FINGERDOWN: this->OnFingerDown(ev.tfinger); break;
		case SDL_FINGERMOTION: this->OnFingerMotion(ev.tfinger); break;
		case SDL_FINGERUP: this->OnFingerUp(ev.tfinger); break;
		default: return false;
	}

	_cursor.in_window = true;
	return true;
}

/** Handle timed events; called every input loop. */
void TouchTrackpad::Tick()
{
	if (this->tap_pending && Clock::now() - this->tap_time >= TAP_DRAG_WINDOW) {
		this->tap_pending = false;
		this->LeftClick();
	}

	/* Keep in sync with cursor moves by others, e.g. a mouse or the cursor being fixed while scrolling. */
	if (this->gesture == Gesture::None) {
		this->cursor_x = _cursor.pos.x;
		this->cursor_y = _cursor.pos.y;
	}
}

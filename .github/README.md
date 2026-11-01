# OpenTTD Touch

**The full OpenTTD for Android, with controls built for a touch screen.**

This is the regular desktop [OpenTTD](https://www.openttd.org) (the open source remake of Transport Tycoon Deluxe) packaged as an Android app. The whole desktop interface is kept, and it follows OpenTTD's releases, so you play the real game, not a cut-down port.

[**Download the latest release**](https://github.com/psyycker/openttd-android/releases/latest) · [Nightly build](https://github.com/psyycker/openttd-android/releases/tag/nightly) · [Report a problem](https://github.com/psyycker/openttd-android/issues) · [Ideas and questions](https://github.com/psyycker/openttd-android/discussions)

<!--
SCREENSHOTS: put images in docs/android/screenshots/ and uncomment, for example:

<p align="center">
  <img src="../docs/android/screenshots/game.png" width="45%">
  <img src="../docs/android/screenshots/controls.png" width="45%">
</p>
-->

## What you get

- **The real game.** The same OpenTTD as on desktop: all the windows and settings, with the free OpenGFX, OpenSFX and OpenMSX base sets included.
- **Touch controls that work like a trackpad.** Move the cursor with one finger, tap to click, two fingers to scroll, pinch to zoom (gestures below).
- **Optional on-screen joystick** to scroll the map with your thumb.
- **Optional on-screen left and right mouse buttons**, for a left- or right-handed position.
- **An editor for the on-screen controls**, so you can place them where your thumbs reach.
- **Mouse and keyboard support.** A USB or Bluetooth mouse and keyboard work as on desktop, with right-drag scrolling, the scroll wheel and all hotkeys.
- **Your files, where you can reach them.** Choose any folder for savegames, scenarios and downloaded content, such as an `OpenTTD` folder in the root of your storage that survives uninstalling.
- **Rendering on OpenGL ES 3**, falling back to software rendering if the device can't do it.
- **Stays current.** New OpenTTD betas, release candidates and final releases are picked up automatically (see below).

## Install

1. Download the APK from the [latest release](https://github.com/psyycker/openttd-android/releases/latest).
2. Open it on your phone or tablet. Android will ask you to allow installing apps from your browser or file manager; allow it.
3. Start **OpenTTD Touch**. On first start it offers a folder for your savegames and settings.

Requires **Android 9 or newer** on a 64-bit ARM (arm64) device, which covers practically all current phones and tablets.

Updates are normal APKs signed with the same key, so install each new one over the old.

## Two versions

| | Release (**OpenTTD Touch**) | Nightly (**OpenTTD Touch Nightly**) |
|---|---|---|
| Based on | The latest OpenTTD final release | OpenTTD's development version (latest beta or release candidate) |
| When it updates | Within a day of a new final release | Every night, when something changed |
| Installs as | The main app | A separate app, so you can keep both |
| Multiplayer | Identifies as that OpenTTD release, so it can join its servers | Only with identical builds |
| Pick this if | You just want to play | You want to try what's coming |

## Touch gestures

| Gesture | Action |
|---|---|
| One finger drag | Move the cursor |
| Tap | Left click |
| Double tap | Double click |
| Tap, then touch again and drag | Drag with the left button held (e.g. build a line of track) |
| Two finger tap | Right click |
| Two finger drag | Scroll the map, or the list under the cursor |
| Pinch | Zoom the map at the cursor |
| Three finger tap | Show or hide the on-screen keyboard |
| Back button or gesture | Escape (closes the top window) |

The app's own settings (on-screen controls, storage location) are in OpenTTD's main menu and settings menu.

## About this project

OpenTTD Touch is an unofficial Android build and is not made by or affiliated with the OpenTTD team. All credit for the game goes to the [OpenTTD contributors](https://github.com/OpenTTD/OpenTTD/blob/master/CREDITS.md); the Android port, touch controls and release automation live in this repository, on top of the OpenTTD sources, with only the changes the Android port needs (mainly in `os/android`, the video driver and the OS layer). Bugs in the game itself belong with [OpenTTD](https://github.com/OpenTTD/OpenTTD/issues); problems with the app, controls or install belong [here](https://github.com/psyycker/openttd-android/issues).

OpenTTD is licensed under the GNU General Public License version 2.0, as is this port. See [COPYING.md](../COPYING.md).

To build it yourself, see [os/android/README.md](../os/android/README.md). OpenTTD's own readme, with the desktop documentation, is [here](../README.md).

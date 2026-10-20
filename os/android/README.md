## OpenTTD for Android

This is the regular desktop OpenTTD, packaged as an Android app. The full
desktop interface is kept; input is designed around a mouse:

- A physical mouse (USB / Bluetooth) and keyboard work as on desktop,
  including right-drag scrolling, the scroll wheel and all hotkeys.
- Without a mouse the touch screen acts as a **trackpad** that moves the cursor:

| Gesture                              | Action                                 |
|--------------------------------------|----------------------------------------|
| One finger drag                      | Move the cursor                        |
| Tap                                  | Left click                             |
| Double tap                           | Double click                           |
| Tap, then touch again and drag       | Drag with the left button held (e.g. build a line of track) |
| Two finger tap                       | Right click                            |
| Two finger drag                      | Scroll the map, or the list under the cursor |
| Pinch                                | Zoom the map at the cursor             |
| Three finger tap                     | Show / hide the on-screen keyboard     |
| Back button / gesture                | Escape (close the top window)          |

Savegames, scenarios, the configuration and downloaded content are stored in
`Android/data/org.openttd.android/files/openttd/` on the device storage, which
is accessible over USB, so files can be copied in and out. On first start the
app offers to use an `OpenTTD` folder in the root of the device storage instead,
which file managers can reach and which survives uninstalling. Any other folder
can be chosen later with the "Storage location" shortcut: long-press the app icon. That needs the "All files access" permission, as OpenTTD uses
plain file I/O; existing files can be copied to the new folder.
The free base sets OpenGFX, OpenSFX and OpenMSX are included.

### Building

Requirements:
- JDK 17 or newer,
- the Android SDK with NDK `29.0.14206865` (see `ndkVersion` in `app/build.gradle.kts`),
- CMake 3.31+ and Ninja on the build host, plus a host C++ compiler (for the host tools),
- a [vcpkg](https://github.com/microsoft/vcpkg) checkout; the dependencies
  listed in the top-level `vcpkg.json` are built for Android automatically.

Create `os/android/local.properties`:
```
sdk.dir=/path/to/Android/Sdk
cmake.dir=/usr
vcpkg.root=/path/to/vcpkg
```

Then, from `os/android`:
```
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Install it with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`.

By default only `arm64-v8a` is built. To build other ABIs (e.g. `x86_64` for
the emulator), set `openttd.abis=arm64-v8a,x86_64` in `local.properties`.

The first build takes a while, as vcpkg builds all dependencies (ICU, curl,
OpenSSL, FreeType, HarfBuzz, SDL2, ...) for each ABI; later builds use the
vcpkg binary cache.

Release builds (`./gradlew assembleRelease`) are signed with the key given by
`openttd.keystore`, `openttd.keystorePassword`, `openttd.keyAlias` and
`openttd.keyPassword` (Gradle properties or `local.properties`), or the
`OPENTTD_KEYSTORE*` / `OPENTTD_KEY_*` environment variables; without one they
are signed with the debug key. The version code is the number of commits.

### Releases

`.github/workflows/deploy.yml` builds the APK on GitHub Actions. Pushing a tag
starting with `v` publishes it as a GitHub release:
```
git tag v1.0.0
git push origin v1.0.0
```
The release key is taken from the repository secrets `ANDROID_KEYSTORE_BASE64`
(the keystore, base64 encoded), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`
and `ANDROID_KEY_PASSWORD`. Keep the keystore safe: updates only install over
an APK signed with the same key.

### Debugging

The game logs to logcat under the `OpenTTD` tag. Command line arguments can be
passed through an intent extra, e.g. for more driver output:
```
adb shell am start -n org.openttd.android/.LauncherActivity --es args "'-d driver=3'"
```

### Rendering

The `sdl-opengl` video driver is used, running on OpenGL ES 3.0 or newer
(see `src/video/opengl.cpp`); when that is unavailable the game falls back to
the software `sdl` driver.

### How it fits together

- `app/src/main/java/org/libsdl/app` is SDL's Java glue, taken unmodified
  from the SDL release matching the vcpkg SDL2 port.
- `LauncherActivity` extracts the game data (packaged as APK assets) to
  internal storage once per installed version, then starts `OpenTTDActivity`,
  which runs the game from `libmain.so`.
- `src/os/android/android_main.cpp` is the native entry point: it redirects
  stdout/stderr to logcat (`adb logcat -s OpenTTD`), sets up the directories and
  calls the normal OpenTTD main.
- `src/video/sdl2_touch.cpp` implements the trackpad emulation.

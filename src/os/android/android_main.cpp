/*
 * This file is part of OpenTTD.
 * OpenTTD is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 2.
 * OpenTTD is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of the GNU General Public License along with OpenTTD. If not, see <https://www.gnu.org/licenses/old-licenses/gpl-2.0>.
 */

/** @file android_main.cpp Main entry for Android; called by SDLActivity. */

#include "../../stdafx.h"
#include "../../openttd.h"
#include "../../crashlog.h"
#include "../../core/random_func.hpp"
#include "../../string_func.h"
#include "android.h"

#include <SDL.h>
#include <SDL_main.h>
#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>

#include "../../safeguards.h"

/** Pipe stdout/stderr are redirected into, so all Debug() output ends up in logcat. */
static int _log_pipe[2];

static void *LogcatThread(void *)
{
	char buf[1024];
	FILE *in = fdopen(_log_pipe[0], "r");
	if (in == nullptr) return nullptr;
	while (fgets(buf, sizeof(buf), in) != nullptr) {
		size_t len = strlen(buf);
		if (len > 0 && buf[len - 1] == '\n') buf[len - 1] = '\0';
		__android_log_write(ANDROID_LOG_INFO, "OpenTTD", buf);
	}
	return nullptr;
}

static void RedirectStdioToLogcat()
{
	setvbuf(stdout, nullptr, _IOLBF, 0);
	setvbuf(stderr, nullptr, _IONBF, 0);
	if (pipe(_log_pipe) != 0) return;
	dup2(_log_pipe[1], STDOUT_FILENO);
	dup2(_log_pipe[1], STDERR_FILENO);

	pthread_t thread;
	if (pthread_create(&thread, nullptr, LogcatThread, nullptr) == 0) pthread_detach(thread);
}

/**
 * Set up the directories OpenTTD searches.
 * - The working directory is the read-only game data extracted from the APK
 *   by the Java side (baseset, lang, ai, game, ...).
 * - The personal directory lives in the app-specific external storage, so
 *   users can copy savegames, scenarios and NewGRFs in and out via USB, or
 *   in a folder of the user's choice (see StorageLocation.java).
 */
static void SetupDirectories()
{
	const char *internal = SDL_AndroidGetInternalStoragePath();
	/* A folder chosen by the user; set up by StorageLocation.java. */
	const char *home = getenv("OPENTTD_HOME");
	if (home == nullptr) home = SDL_AndroidGetExternalStoragePath();
	if (home == nullptr) home = internal;

	if (home != nullptr) {
		std::string home_dir(home); // setenv may invalidate the result of getenv
		setenv("HOME", home_dir.c_str(), 1);
		setenv("XDG_DATA_HOME", home_dir.c_str(), 1);
		setenv("XDG_CONFIG_HOME", home_dir.c_str(), 1);
	}

	if (internal != nullptr) {
		std::string data_dir = std::string(internal) + "/data";
		if (chdir(data_dir.c_str()) != 0) {
			__android_log_print(ANDROID_LOG_ERROR, "OpenTTD", "Cannot change to data directory %s", data_dir.c_str());
		}
	}
}

/** Open the settings screen of the Android app (storage location, ...); see OpenTTDActivity.openAppSettings. */
void ShowAndroidAppSettings()
{
	JNIEnv *env = static_cast<JNIEnv *>(SDL_AndroidGetJNIEnv());
	jobject activity = static_cast<jobject>(SDL_AndroidGetActivity());
	if (env == nullptr || activity == nullptr) return;

	jclass cls = env->GetObjectClass(activity);
	jmethodID method = env->GetMethodID(cls, "openAppSettings", "()V");
	if (method != nullptr) env->CallVoidMethod(activity, method);
	if (env->ExceptionCheck()) env->ExceptionClear();

	env->DeleteLocalRef(cls);
	env->DeleteLocalRef(activity);
}

/* SDL_main.h renames this to SDL_main and exports it for SDLActivity. */
int main(int argc, char *argv[])
{
	RedirectStdioToLogcat();
	SetupDirectories();

	/* Make sure our arguments contain only valid UTF-8 characters. */
	std::vector<std::string_view> params;
	for (int i = 0; i < argc; ++i) {
		StrMakeValidInPlace(argv[i]);
		params.emplace_back(argv[i]);
	}

	CrashLog::InitialiseCrashLog();

	SetRandomSeed(time(nullptr));

	signal(SIGPIPE, SIG_IGN);

	int ret = openttd_main(params);

	/* The process is kept alive by Android; make sure the next start is clean. */
	exit(ret);
}

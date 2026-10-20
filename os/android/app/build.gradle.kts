import java.io.FileInputStream
import java.net.URI
import java.util.Properties
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
}

/* The OpenTTD source tree; this project lives in os/android. */
val openttdRoot: File = rootDir.resolve("../..").canonicalFile

val localProperties = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) FileInputStream(file).use { load(it) }
}

/** Look up a setting as Gradle property, in local.properties, or as environment variable. */
fun setting(name: String, env: String): String? =
    (findProperty(name) as String?) ?: localProperties.getProperty(name) ?: System.getenv(env)

val vcpkgRoot = setting("vcpkg.root", "VCPKG_ROOT")
    ?: throw GradleException("Point vcpkg.root (local.properties) or VCPKG_ROOT to a vcpkg checkout; see README.md")

/* Comma separated list of ABIs to build; arm64-v8a covers practically all current devices. */
val abis = (setting("openttd.abis", "OPENTTD_ABIS") ?: "arm64-v8a").split(",").map { it.trim() }

/* Version from the project() call in the main CMakeLists.txt: an indented line like "    VERSION 16.0". */
val openttdVersion: String = Regex("""(?m)^\s+VERSION\s+(\d+)\.(\d+)""")
    .find(openttdRoot.resolve("CMakeLists.txt").readText())!!.let { "${it.groupValues[1]}.${it.groupValues[2]}" }

/* Always increasing version code for Android: the number of commits. */
val commitCount: Int = runCatching {
    providers.exec { commandLine("git", "-C", openttdRoot.path, "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

/* Nightly builds are a separate app, so they can be installed next to the stable release. */
val nightly = setting("openttd.nightly", "OPENTTD_NIGHTLY").let { it == "1" || it == "true" }

/* Release signing key, e.g. from CI secrets; without it release builds are signed with the debug key. */
val releaseKeystore = setting("openttd.keystore", "OPENTTD_KEYSTORE")

val hostToolsDir: File = layout.buildDirectory.dir("host-tools").get().asFile
val gameDataDir: File = layout.buildDirectory.dir("generated/game-data").get().asFile
val vcpkgInstalledDir: File = layout.buildDirectory.dir("vcpkg_installed").get().asFile

android {
    namespace = "org.openttd.android"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "org.openttd.android"
        minSdk = 28
        targetSdk = 36
        versionCode = commitCount
        versionName = setting("openttd.versionName", "OPENTTD_VERSION_NAME") ?: "$openttdVersion-android"
        if (nightly) applicationIdSuffix = ".nightly"
        resValue("string", "app_name", if (nightly) "OpenTTD Nightly" else "OpenTTD")

        ndk {
            abiFilters += abis
        }

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_TOOLCHAIN_FILE=${rootDir.resolve("cmake/android-vcpkg-toolchain.cmake")}",
                    "-DVCPKG_ROOT=$vcpkgRoot",
                    "-DVCPKG_INSTALLED_DIR=$vcpkgInstalledDir",
                    "-DHOST_BINARY_DIR=$hostToolsDir",
                    "-DANDROID_ASSETS_DIR=$gameDataDir",
                    "-DANDROID_STL=c++_shared",
                    "-DOPTION_USE_ASSERTS=OFF",
                )
                targets += "openttd"
            }
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = setting("openttd.keystorePassword", "OPENTTD_KEYSTORE_PASSWORD")
                keyAlias = setting("openttd.keyAlias", "OPENTTD_KEY_ALIAS")
                keyPassword = setting("openttd.keyPassword", "OPENTTD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            externalNativeBuild {
                cmake {
                    /* An unoptimised OpenTTD is unplayable; keep debug info but optimise. */
                    arguments += "-DCMAKE_BUILD_TYPE=RelWithDebInfo"
                }
            }
        }
        release {
            isMinifyEnabled = false
            /* Without a release key, sign with the debug key so the build can still be sideloaded. */
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            externalNativeBuild {
                cmake {
                    arguments += "-DCMAKE_BUILD_TYPE=Release"
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = openttdRoot.resolve("CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs(gameDataDir)
        }
    }

    buildFeatures {
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            /* libmain.so contains the ICU data and compresses well; keep the APK small. */
            useLegacyPackaging = true
        }
    }
}

/* Host tools (strgen, settingsgen) are needed to generate sources while cross-compiling. */
val buildHostTools by tasks.registering(Exec::class) {
    description = "Builds the OpenTTD host tools needed for cross-compiling"
    inputs.dir(openttdRoot.resolve("src/strgen"))
    inputs.dir(openttdRoot.resolve("src/settingsgen"))
    outputs.dir(hostToolsDir)
    commandLine("sh", "-c",
        "cmake -S '$openttdRoot' -B '$hostToolsDir' -DOPTION_TOOLS_ONLY=ON -DCMAKE_BUILD_TYPE=Release && " +
        "cmake --build '$hostToolsDir' --target tools --parallel")
}

/* Free base sets, so the game is playable without the original TTD data files. */
abstract class DownloadBaseSets : DefaultTask() {
    @get:Input
    abstract val baseSets: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val target = outputDir.get().asFile.resolve("openttd/baseset")
        target.mkdirs()
        for (path in baseSets.get()) {
            /* Each zip contains a single .tar, which OpenTTD reads directly. */
            URI("https://cdn.openttd.org/$path").toURL().openStream().use { stream ->
                ZipInputStream(stream).use { zip ->
                    generateSequence { zip.nextEntry }.filter { !it.isDirectory }.forEach { entry ->
                        target.resolve(File(entry.name).name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
    }
}

val downloadBaseSets by tasks.registering(DownloadBaseSets::class) {
    description = "Downloads OpenGFX, OpenSFX and OpenMSX"
    baseSets.set(listOf(
        "opengfx-releases/8.0/opengfx-8.0-all.zip",
        "opensfx-releases/1.0.3/opensfx-1.0.3-all.zip",
        "openmsx-releases/0.4.2/openmsx-0.4.2-all.zip",
    ))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadBaseSets, DownloadBaseSets::outputDir)
    }
}

tasks.configureEach {
    if (name.startsWith("configureCMake")) dependsOn(buildHostTools)
    /* The game data is produced by the native build (see ANDROID_ASSETS_DIR in CMakeLists.txt). */
    Regex("merge(\\w+)Assets").matchEntire(name)?.let {
        dependsOn("externalNativeBuild${it.groupValues[1]}")
    }
}

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

/* Version from the main CMakeLists.txt, e.g. "project(openttd VERSION 16.0". */
val openttdVersion: String = Regex("""VERSION\s+(\d+)\.(\d+)""")
    .find(openttdRoot.resolve("CMakeLists.txt").readText())!!.let { "${it.groupValues[1]}.${it.groupValues[2]}" }

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
        versionCode = openttdVersion.split(".").let { it[0].toInt() * 10000 + it[1].toInt() * 100 }
        versionName = "$openttdVersion-android"

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
            /* Signed with the debug key so release builds can be sideloaded; use a real key for distribution. */
            signingConfig = signingConfigs.getByName("debug")
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

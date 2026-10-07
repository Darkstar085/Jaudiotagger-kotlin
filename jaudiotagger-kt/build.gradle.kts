import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    `maven-publish`
}

group = "org.jaudiotagger"
version = "0.1.0-SNAPSHOT"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm {
        // keep the bytecode runnable on older desktop JVMs
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("net.jthink:jaudiotagger:3.0.1")
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("androidx.test:runner:1.7.0")
                implementation("androidx.test.ext:junit:1.2.1")
            }
        }
    }
}

val androidTestAssetFiles = listOf(
    "issue52.mp3",
    "test23.mp3",
    "testV1Cbr128ID3v1.mp3",
    "testV1Cbr128.mp3",
    "test.flac",
    "test.ogg",
    "test.m4a",
    "test.wav",
    "test119.aif",
    "test1.wma",
    "test122.dsf",
    "test0001.ape",
    "test0001.wv",
    "coverart.png",
)

val syncAndroidTestAssets = tasks.register<Sync>("syncAndroidTestAssets") {
    from(rootProject.projectDir.resolve("testdata")) {
        include(androidTestAssetFiles)
    }
    into(layout.buildDirectory.dir("generated/androidTestAssets/testdata"))
}

android {
    namespace = "org.jaudiotagger.kt"
    compileSdk = 35
    defaultConfig {
        // android.system.Os.pread/pwrite/ftruncate need API 21
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        getByName("androidTest") {
            assets.srcDir(layout.buildDirectory.dir("generated/androidTestAssets"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Both jvm and native tests read audio samples from the repo's testdata directory
val testDataDir = rootProject.projectDir.resolve("testdata").absolutePath

tasks.withType<Test>().configureEach {
    environment("TESTDATA_DIR", testDataDir)
}

tasks.withType<KotlinNativeTest>().configureEach {
    environment("TESTDATA_DIR", testDataDir)
}

// Kotlin 2.4 registers prepareKotlinIdeaImport on modules; some IDE versions still
// request prepareKotlinBuildScriptModel on subprojects (it lives on the root only).
tasks.register("prepareKotlinBuildScriptModel") {
    dependsOn(tasks.named("prepareKotlinIdeaImport"))
}

tasks.matching { it.name == "mergeDebugAndroidTestAssets" }.configureEach {
    dependsOn(syncAndroidTestAssets)
}

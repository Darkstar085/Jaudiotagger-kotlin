import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.vanniktech.maven.publish")
    signing
}

group = "io.github.darkstar085"
version = "0.1.0"

signing {
    val signingKey = providers.environmentVariable("SIGNING_KEY").orNull
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull

    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "jaudiotagger-kt", version.toString())

    pom {
        name = "Jaudiotagger Kotlin"
        description = "Kotlin Multiplatform audio metadata library."
        url = "https://github.com/Darkstar085/Jaudiotagger-kotlin"
        licenses {
            license {
                name = "GNU Lesser General Public License, Version 2.1"
                url = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"
                distribution = "https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html"
            }
        }
        developers {
            developer {
                id = "Darkstar085"
                name = "S I P U N"
                url = "https://github.com/Darkstar085"
            }
        }
        scm {
            url = "https://github.com/Darkstar085/Jaudiotagger-kotlin"
            connection = "scm:git:git://github.com/Darkstar085/Jaudiotagger-kotlin.git"
            developerConnection = "scm:git:ssh://git@github.com/Darkstar085/Jaudiotagger-kotlin.git"
        }
    }
}

kotlin {
    android {
        namespace = "org.jaudiotagger.kt"
        compileSdk = 35
        minSdk = 21
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            execution = "HOST"
        }
    }
    jvm { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        val androidDeviceTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("androidx.test:runner:1.7.0")
                implementation("androidx.test.ext:junit:1.2.1")
            }
        }
    }
}

val androidTestAssetFiles = listOf(
    "issue52.mp3", "test23.mp3", "testV1Cbr128ID3v1.mp3", "testV1Cbr128.mp3",
    "test.flac", "test.ogg", "test.m4a", "test.wav", "test119.aif", "test1.wma",
    "test122.dsf", "test0001.ape", "test0001.wv", "coverart.png",
)

val syncAndroidTestAssets = tasks.register<Sync>("syncAndroidTestAssets") {
    from(rootProject.projectDir.resolve("testdata")) { include(androidTestAssetFiles) }
    into(layout.buildDirectory.dir("generated/androidTestAssets/testdata"))
}

val testDataDir = rootProject.projectDir.resolve("testdata").absolutePath
tasks.withType<Test>().configureEach { environment("TESTDATA_DIR", testDataDir) }
tasks.withType<KotlinNativeTest>().configureEach { environment("TESTDATA_DIR", testDataDir) }
tasks.matching { it.name == "mergeDebugAndroidTestAssets" }.configureEach {
    dependsOn(syncAndroidTestAssets)
}

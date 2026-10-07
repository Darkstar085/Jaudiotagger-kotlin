pluginManagement {
    plugins {
        kotlin("multiplatform") version "2.4.20"
        id("com.android.kotlin.multiplatform.library") version "9.4.0"
        id("com.vanniktech.maven.publish") version "0.37.0"
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

rootProject.name = "jaudiotagger-kt"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

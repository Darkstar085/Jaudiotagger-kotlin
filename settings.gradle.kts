pluginManagement {
    plugins {
        kotlin("multiplatform") version "2.4.20"
        id("com.android.library") version "8.11.2"
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

// Root build of the jaudiotagger repository.
//
// The actual library lives in the :jaudiotagger-kt module (Kotlin Multiplatform).
// The original Java sources are kept in src/ (built by the legacy pom.xml) purely
// as a porting reference; they are not part of this Gradle build.

plugins {
    kotlin("multiplatform") version "2.4.20" apply false
    id("com.android.library") version "8.11.2" apply false
}

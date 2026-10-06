plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // KSP2. Independent of the Kotlin compiler version since 2.3.0 and
    // compatible with AGP 9 built-in Kotlin since 2.3.1 — do not re-tie this
    // version to the Kotlin version.
    id("com.google.devtools.ksp") version "2.3.12" apply false
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    // Classpath pin only. AGP 9 compiles Kotlin itself; applying this plugin
    // conflicts with built-in Kotlin.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
}

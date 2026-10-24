import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

android {
    namespace = "local.z9x.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "local.z9x.manager"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // CI / default: sign with Android debug keystore so Releases work
            // without user-provided secrets. Replace with your own keystore
            // (see README) for Play / long-term update continuity.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}

// Produce a stable Release filename: Z9XManager-v{versionName}.apk
tasks.register<Copy>("namedReleaseApk") {
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*-release.apk", "*.apk")
    exclude("*-unsigned.apk", "output-metadata.json")
    into(layout.buildDirectory.dir("outputs/apk/named"))
    rename { _ -> "Z9XManager-v${android.defaultConfig.versionName}.apk" }
}

afterEvaluate {
    tasks.named("assembleRelease").configure {
        finalizedBy("namedReleaseApk")
    }
}

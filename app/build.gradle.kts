plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Room's annotation processor runs on KSP2. KSP >= 2.3.0 is version-independent
    // of the Kotlin compiler and KSP >= 2.3.1 supports AGP 9 built-in Kotlin, so
    // this module does not need the kotlin-android plugin or a Kotlin-tied KSP version.
    id("com.google.devtools.ksp")
}

android {
    // Namespace is the R/BuildConfig package only; it is deliberately decoupled
    // from applicationId, which is left unchanged so the installed identity of
    // the app does not change in this phase. The engine now lives in
    // com.thermalsentinel.engine.* next to the existing com.thermalsentinel.ui.*
    // screens, which is why the namespace was promoted from .ui to the root.
    namespace = "com.thermalsentinel"
    compileSdk = 37

    defaultConfig {
        // Store identity. Unchanged in this phase on purpose: changing it would
        // install as a different app. Finalise before the first Play upload.
        applicationId = "com.thermalsentinel.ui"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0-engine"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Local persistence for thermal/battery history. No cloud, no sync.
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Home-screen widget. Glance 1.2.0 is the current stable release;
    // glance-material3 is intentionally not used so the widget does not inherit
    // a Material theming dependency.
    implementation("androidx.glance:glance:1.2.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

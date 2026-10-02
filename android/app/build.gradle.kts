import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.gms.google-services")
    id("com.google.dagger.hilt.android")
}

// FlashcardQuiz backend origin. Blank disables backend-first generation/OCR.
val backendUrlValue: String = run {
    val fromEnv = System.getenv("FLASHQUIZ_BACKEND_URL")?.trim().orEmpty()
    val fromGradle = if (fromEnv.isNotEmpty()) "" else
        project.findProperty("flashquiz.backendUrl")?.toString()?.trim().orEmpty()
    val fromLocalProps = if (fromEnv.isNotEmpty() || fromGradle.isNotEmpty()) "" else run {
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        props.getProperty("BACKEND_URL")?.trim().orEmpty()
    }
    fromEnv.ifEmpty { fromGradle.ifEmpty { fromLocalProps } }
}
val backendUrlLiteral =
    "\"" + backendUrlValue.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Staged feature flags (see docs/GOLIVE.md): ads and payments ship DISABLED
// until the AdMob / Play Console / RevenueCat accounts are live. Flip with
// -Pflashquiz.ads=true / -Pflashquiz.payments=true (or FLASHQUIZ_ADS /
// FLASHQUIZ_PAYMENTS env vars) when staging a monetized build.
fun stagedFlag(envName: String, propName: String): String {
    val fromEnv = System.getenv(envName)?.trim()?.lowercase().orEmpty()
    val raw = if (fromEnv.isNotEmpty()) fromEnv
    else project.findProperty(propName)?.toString()?.trim()?.lowercase().orEmpty()
    return if (raw == "true" || raw == "1") "true" else "false"
}

android {
    namespace = "com.syedali.flashquiz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.syedali.flashquiz"
        minSdk = 24
        targetSdk = 36
        versionCode = 9
        versionName = "2.5.0"

        // AdMob App ID
        manifestPlaceholders["ADMOB_APP_ID"] = "ca-app-pub-7129470803481646~5471062878"

        // FlashcardQuiz backend origin (e.g. https://flashcards.example.com).
        // Blank disables it and the app generates entirely on-device.
        // Set with -Pflashquiz.backendUrl=..., FLASHQUIZ_BACKEND_URL, or
        // BACKEND_URL in local.properties.
        buildConfigField(
            "String",
            "BACKEND_URL",
            backendUrlLiteral
        )
        buildConfigField("boolean", "ADS_ENABLED", stagedFlag("FLASHQUIZ_ADS", "flashquiz.ads"))
        buildConfigField("boolean", "PAYMENTS_ENABLED", stagedFlag("FLASHQUIZ_PAYMENTS", "flashquiz.payments"))
    }

    signingConfigs {
        create("release") {
            val localProps = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun secret(name: String): String =
                System.getenv(name) ?: localProps.getProperty(name) ?: ""
            storeFile = file("release-key.jks")
            storePassword = secret("RELEASE_STORE_PASSWORD")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS") ?: "flashcardquiz"
            keyPassword = secret("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    // GDPR consent (UMP) — required before serving personalized ads to EEA/UK users
    implementation("com.google.android.ump:user-messaging-platform:3.1.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // RevenueCat
    implementation("com.revenuecat.purchases:purchases:6.9.0")

    // Compose (interop: ComposeView inside XML + theme)
    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime-livedata")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Room (Phase 2: data layer mapped 1:1 to flashcardquiz.db schema)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // DataStore (replaces SharedPreferences incrementally)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Hilt (DI)
    implementation("com.google.dagger:hilt-android:2.51.1")
    kapt("com.google.dagger:hilt-android-compiler:2.51.1")

    // Retrofit (network)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Unit tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

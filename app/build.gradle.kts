import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.github.triplet.play")
}

// Load signing credentials from keystore.properties (gitignored).
// See keystore.properties.example at repo root for the expected schema.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.skylegends.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "sky.legends"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val storeFileName = keystoreProps.getProperty("storeFile") ?: "release-keystore.jks"
            val resolvedStoreFile = rootProject.file(storeFileName)
            if (resolvedStoreFile.exists() && keystoreProps.getProperty("storePassword") != null) {
                storeFile = resolvedStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
            // If keystore.properties is missing, release builds will fail with a clear
            // error from AGP rather than silently signing with bad credentials.
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
            // Only attach the release signing config if credentials were actually loaded.
            if (keystoreProps.getProperty("storePassword") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}

// Gradle Play Publisher — uploads the signed AAB to Google Play.
// Key file is gitignored (see .gitignore). Usage:
//   ./gradlew publishReleaseBundle -Ptrack=<track id>
// Track defaults to "internal" so an accidental run can't disturb production.
// NOTE: the app must be published manually via the Play Console once before the
// Play Developer API will accept uploads for a brand-new app.
play {
    serviceAccountCredentials.set(rootProject.file("play-service-account.json"))
    defaultToAppBundles.set(true)
    track.set(providers.gradleProperty("track").getOrElse("internal"))
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.COMPLETED)
}

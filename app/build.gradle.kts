import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release-Signing ausschließlich über Umgebungsvariablen (CI Secrets).
// Kein Key im Repository, kein generierter Key, kein Fake-Signing.
val keystorePath: String? = System.getenv("D88_KEYSTORE_PATH")
val keystorePass: String? = System.getenv("D88_KEYSTORE_PASS")
val keyAlias: String? = System.getenv("D88_KEY_ALIAS")
val keyPass: String? = System.getenv("D88_KEY_PASS")
val releaseSigningAvailable =
    keystorePath != null && keystorePass != null && keyAlias != null && keyPass != null &&
        File(keystorePath).exists()

android {
    namespace = "de.d88.platform"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.d88.platform"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-prototype"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = File(keystorePath!!)
                storePassword = keystorePass!!
                keyAlias = keyAlias!!
                keyPassword = keyPass!!
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningAvailable) {
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    // org.json wird auf dem Android-Gerät mitgeliefert; für JVM-Unit-Tests nötig.
    testImplementation("org.json:json:20240303")
}

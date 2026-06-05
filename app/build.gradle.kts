plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.flightstats"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.flightstats"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../flightstats.jks")
            storePassword = "flightstats123"
            keyAlias = "flightstats-key"
            keyPassword = "flightstats123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.mpandroidchart)
    implementation(libs.gson)
    implementation(libs.osmdroid)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta2")
    testImplementation(libs.junit)
    testImplementation("com.google.mlkit:genai-prompt:1.0.0-beta2")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
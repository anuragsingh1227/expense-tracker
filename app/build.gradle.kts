plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

import java.util.Properties

android {
    namespace = "com.expensetracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.expensetracker.offline"
        minSdk = 29
        targetSdk = 34
        versionCode = 116
        versionName = "1.1.16"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Overridden per flavor.
        buildConfigField("boolean", "FEATURE_AUTO_SMS", "false")
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("store") {
            dimension = "distribution"
            // Play / Amazon / Galaxy Store — no SMS permission group.
            buildConfigField("boolean", "FEATURE_AUTO_SMS", "false")
            resValue("string", "app_name", "Expense Tracker")
        }
        create("sms") {
            dimension = "distribution"
            applicationIdSuffix = ".sms"
            // Sideload / F-Droid / GitHub — full on-device SMS auto-import.
            buildConfigField("boolean", "FEATURE_AUTO_SMS", "true")
            resValue("string", "app_name", "Expense Tracker SMS")
        }
    }

    signingConfigs {
        create("release") {
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.isFile) {
                val props = Properties().apply { propsFile.inputStream().use { load(it) } }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile?.isFile == true) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation("org.json:json:20240303")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.espresso.core)
}

import java.util.Properties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "cn.mudlife.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.mudlife.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 35
        versionName = "1.4.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Permanent release credentials live outside the checkout and build outputs.
            val propFile = File(System.getProperty("user.home"), ".android/mudlife-signing.properties")
            val props = Properties()
            if (propFile.exists()) {
                propFile.reader(Charsets.UTF_8).use { props.load(it) }
            }
            val ksPath = System.getenv("MUDLIFE_KEYSTORE_FILE") ?: props.getProperty("KEYSTORE_FILE")
            val ks = ksPath?.let { file(it) }
            if (ks != null && ks.isFile) {
                storeFile = ks
                storePassword = System.getenv("MUDLIFE_KEYSTORE_PASSWORD") ?: props.getProperty("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MUDLIFE_KEY_ALIAS") ?: props.getProperty("KEY_ALIAS")
                keyPassword = System.getenv("MUDLIFE_KEY_PASSWORD") ?: props.getProperty("KEY_PASSWORD")
            }
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".refresh"
            versionNameSuffix = "-refresh-preview"
        }
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Never silently generate or substitute a release key.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val signing = android.signingConfigs.getByName("release")
        check(signing.storeFile?.isFile == true && !signing.storePassword.isNullOrBlank() &&
            !signing.keyAlias.isNullOrBlank() && !signing.keyPassword.isNullOrBlank()) {
            "MudLife release signing is missing. Restore the permanent keystore and credentials; do not generate a replacement."
        }
        val keyStore = KeyStore.getInstance("PKCS12")
        signing.storeFile!!.inputStream().use { keyStore.load(it, signing.storePassword!!.toCharArray()) }
        check(keyStore.isKeyEntry(signing.keyAlias)) { "The release private key is missing." }
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(keyStore.getCertificate(signing.keyAlias).encoded)
            .joinToString("") { "%02x".format(it) }
        check(fingerprint == rootProject.file("signing/release-certificate.sha256").readText().trim()) {
            "Wrong release signing identity. Restore the fixed MudLife key; do not rotate it."
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.mqtt.paho)
    implementation(libs.mqtt.android)
    implementation(libs.security.crypto)

    implementation("com.google.zxing:core:3.5.3")

    // 扫码
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}

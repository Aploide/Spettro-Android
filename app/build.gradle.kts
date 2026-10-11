plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// True when this Gradle run builds or installs release APKs and no bundle:
// AGP fails bundleRelease with ABI splits on ("Multiple shrunk-resources
// files found"), so the splits, and the versionCodes that go with them,
// stay off for any run that asks for a bundle.
val releaseApkSplits: Boolean = gradle.startParameter.taskNames
    .map { it.substringAfterLast(':') }
    .let { tasks ->
        tasks.none { it.startsWith("bundle") } &&
            tasks.any { it == "assembleRelease" || it == "installRelease" }
    }

android {
    namespace = "to.eyed.spettro.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "to.eyed.spettro.mobile"
        minSdk = 33
        targetSdk = 37
        versionCode = 18
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // The Play build is signed with the upload key and cannot be
            // upgraded in place by a locally-signed one. Rather than making
            // "run it on my phone" mean "uninstall the app you actually use"
            // — which also destroys the host pairing, since credentials live
            // in app-private storage and allowBackup is off — the debug build
            // installs alongside it under its own id.
            //
            // Safe to do here precisely because there is nothing keyed to the
            // application id: no google-services.json, so there is no FCM
            // registration to break. Both copies do register the
            // spettro-pair:// link, so with both installed Android asks which
            // one should open it — the right outcome for a developer. The
            // namespace is untouched, so BuildConfig and R stay put.
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // The label comes from src/debug/res, not resValue(): app_name
            // already exists in the main source set and declaring it twice is
            // a duplicate-resource error rather than an override.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    // One APK per ABI for sideloading: most of a universal APK is ML Kit's
    // bundled barcode model (libbarhopper_v3.so), once for every ABI. The
    // splits only go on for a build of release APKs (see releaseApkSplits),
    // so debug builds, unit tests and the screenshot harness stay one APK,
    // and bundleRelease, which AGP refuses to build with ABI splits on,
    // splits by itself. versionCodes per ABI: see androidComponents below.
    splits {
        abi {
            isEnable = releaseApkSplits
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            // Every ABI, x86 included: the APK there was before the splits.
            isUniversalApk = true
        }
    }
}

// Each split release APK's versionCode is versionCode × 10 + an ABI digit:
// universal 0, armeabi-v7a 1, arm64-v8a 2, x86_64 3 (18 -> 180..183). Any
// APK of a newer release upgrades any APK of an older one, and within a
// release a split may replace the universal APK. Only with the splits on:
// the bundle, and any build that leaves them off, keep the plain versionCode.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        if (!releaseApkSplits) return@onVariants
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull {
                it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }?.identifier
            val digit = when (abi) {
                "armeabi-v7a" -> 1
                "arm64-v8a" -> 2
                "x86_64" -> 3
                else -> 0
            }
            val base = output.versionCode.orNull ?: return@forEach
            output.versionCode.set(base * 10 + digit)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
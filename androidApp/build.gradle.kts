plugins {
    alias(libs.plugins.androidApplication)
}

val appVersionCode = providers.gradleProperty("toaf.versionCode").get().toInt()
val appVersionName = providers.gradleProperty("toaf.versionName").get()

android {
    namespace = "pl.misek.toafmobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "pl.misek.toafmobile"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // androidx.sqlite's bundled native driver (libsqliteJni.so) ships a slice per ABI.
    // Play's automatic per-device ABI splitting of the App Bundle has been observed to
    // omit the matching slice for some devices (e.g. x86_64 emulators), causing
    // "dlopen failed: library libsqliteJni.so not found" at startup. Disabling the ABI
    // split makes every install carry all ABIs, trading download size for reliability.
    bundle {
        abi {
            enableSplit = false
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-android.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.compose.splashscreen)
    debugImplementation(libs.androidx.ui.tooling)
}

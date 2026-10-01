import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appVersionCode = 110
val appVersionName = "1.0.10"
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.aboutLibraries)
}

kotlin {
    jvmToolchain(21)
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.sqlite.bundled)
            implementation(libs.koin.android)
            implementation(libs.usb.serial.android)
            implementation(libs.google.billing)
            implementation(libs.google.ads)
        }
        commonMain.dependencies {
            implementation(libs.compose.ui.tooling)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.material.iconsExtended)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.room.runtime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)
            //implementation(libs.compose.icons.extended)
            implementation(libs.vico.compose.m3)
            implementation(libs.sentry.kmp)
            implementation(libs.kotlinx.datetime)
            implementation(libs.co.touchlab.ketmin)
            implementation(libs.aboutLibraries.compose)
            implementation(libs.aboutLibraries.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.sqlite.bundled)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.jserialcomm)
        }
    }
}

android {
    namespace = "pl.misek.toafmobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "pl.misek.toafmobile"
        minSdk = 24
        targetSdk = 35
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
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.compose.ui.tooling)
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}


compose.desktop {
    application {
        mainClass = "pl.misek.toafmobile.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Rpm, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Pkg)
            packageName = "Toaf Desktop"
            version = appVersionName
            packageVersion = appVersionName
            linux {
                appCategory = "Utility"
                menuGroup = "Utility"
                shortcut = true
                iconFile.set(project.file("src/commonMain/composeResources/drawable/toaf_icon_linux.png"))
                debMaintainer = "toaf@roverki.eu"
            }
            windows {
                menu = true
                menuGroup = "Toaf"
                shortcut = true
                iconFile.set(project.file("src/commonMain/composeResources/drawable/toaf_icon_windows.ico"))
                upgradeUuid = "68d0ed02-7125-4d98-856c-5ef07c0ad3ef"
            }
            modules(
                "java.instrument",
                "java.management",
                "jdk.unsupported"
            )
        }
        buildTypes.release.proguard{
            configurationFiles.from(project.file("proguard-desktop.pro"))
            // Opcjonalnie: Jeśli aplikacja nadal crashuje, wyłącz zaciemnianie (obfuscację),
            // zachowując tylko "minifikację" (usuwanie nieużywanego kodu)
            // obfuscate.set(false)
        }
    }
}
buildkonfig {
    packageName = "pl.misek.toafmobile"
    defaultConfigs {
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.INT, "VERSION_CODE", appVersionCode.toString())
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "VERSION_NAME", appVersionName)
    }
}
aboutLibraries {
    export {
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        prettyPrint = true
    }
}

tasks.matching {
    it.name.startsWith("copyNonXmlValueResources") ||
            it.name.startsWith("generateComposeResClass")
}.configureEach {
    dependsOn("exportLibraryDefinitions")
}
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val versionCode = 100
val versionName = "1.0.0"
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm()

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
            implementation("org.jetbrains.compose.ui:ui-tooling:1.10.3")
            implementation("org.jetbrains.compose.runtime:runtime:1.10.2")
            implementation("org.jetbrains.compose.foundation:foundation:1.10.2")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.ui:ui:1.10.2")
            implementation("org.jetbrains.compose.components:components-resources:1.10.2")
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.room.runtime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.navigation.compose)
            implementation(libs.compose.icons.extended)
            implementation(libs.vico.compose.m3)
            implementation(libs.sentry.kmp)
            implementation(libs.kotlinx.datetime)
            implementation(libs.co.touchlab.ketmin)
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
        versionCode = versionCode
        versionName = versionName
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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
    debugImplementation("org.jetbrains.compose.ui:ui-tooling:1.10.2")
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
}


compose.desktop {
    application {
        mainClass = "pl.misek.toafmobile.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Toaf Desktop"
            version = versionName
            packageVersion = versionName
            linux {
                iconFile.set(project.file("src/commonMain/composeResources/drawable/toaf_icon_linux.png"))
            }
            windows {
                iconFile.set(project.file("src/commonMain/composeResources/drawable/toaf_icon_windows.ico"))
                menuGroup = "Toaf"
                upgradeUuid = "68d0ed02-7125-4d98-856c-5ef07c0ad3ef"
            }
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
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.INT, "VERSION_CODE", versionCode.toString())
        buildConfigField(com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "VERSION_NAME", versionName)
    }
}
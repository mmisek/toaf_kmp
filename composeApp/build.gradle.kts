import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appVersionCode = providers.gradleProperty("toaf.versionCode").get().toInt()
val appVersionName = providers.gradleProperty("toaf.versionName").get()
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
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
    android {
        namespace = "pl.misek.toafmobile.shared"
        compileSdk = 37
        minSdk = 24
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        androidResources {
            enable = true
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

dependencies {
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
                dirChooser = true
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

// Podgląd stringów w edytorze IDE pokazuje pierwszy ResourceItem z wygenerowanych akcesorów,
// a generator układa warianty alfabetycznie (values-es przed values). Przesuwamy wariant
// domyślny (angielski) na początek - wybór zasobu w runtime nie zależy od kolejności.
tasks.matching { it.name.startsWith("generateResourceAccessorsFor") }.configureEach {
    doLast {
        val itemsBlock = Regex("""(?:\r?\n[ \t]*ResourceItem\(.*\),)+""")
        outputs.files.asFileTree.matching { include("**/*.kt") }.forEach { file ->
            val text = file.readText()
            val fixed = itemsBlock.replace(text) { match ->
                val nl = if (match.value.startsWith("\r\n")) "\r\n" else "\n"
                val (default, localized) = match.value.lines().drop(1)
                    .partition { it.trimStart().startsWith("ResourceItem(setOf(),") }
                (default + localized).joinToString("") { nl + it }
            }
            if (fixed != text) file.writeText(fixed)
        }
    }
}
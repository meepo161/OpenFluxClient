import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

/** The app version: -PappVersion=1.2.3 (the release workflow passes the tag). */
val appVersion = (findProperty("appVersion") as String?)?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "2.0.0"

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    // Skia's Windows natives (skiko) come with the host's runtime above only
    // when building on Windows; a Windows package needs them whatever builds it.
    if (findProperty("windowsPackage") == "true") implementation(compose.desktop.windows_x64)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.compose.components.resources)
    testImplementation(libs.kotlin.testJunit)
}

compose.desktop {
    application {
        mainClass = "io.openflux.desktop.MainKt"
        jvmArgs("-Dopenflux.version=$appVersion")
        // JCEF (the built-in browser) reaches into AWT internals.
        jvmArgs("--add-opens", "java.desktop/sun.awt=ALL-UNNAMED")
        jvmArgs("--add-opens", "java.desktop/java.awt.peer=ALL-UNNAMED")
        if (System.getProperty("os.name").contains("Mac")) {
            jvmArgs("--add-opens", "java.desktop/sun.lwawt=ALL-UNNAMED")
            jvmArgs("--add-opens", "java.desktop/sun.lwawt.macosx=ALL-UNNAMED")
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "OpenFlux"
            packageVersion = appVersion
            description = "OpenFlux desktop client"
            vendor = "meepo161"
            // The core (and wintun.dll on Windows) in resources/<windows|macos|linux>,
            // put there by scripts/build-core.sh or the release workflow.
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            modules("java.instrument", "java.management", "java.net.http", "jdk.crypto.ec", "jdk.unsupported")

            windows {
                menuGroup = "OpenFlux"
                upgradeUuid = "3F0C7B52-9A2E-4C1B-8E77-5D2A6B1E9C40"
                shortcut = true
                perUserInstall = true
            }
            macOS {
                bundleID = "io.openflux.desktop"
                dockName = "OpenFlux"
                appCategory = "public.app-category.utilities"
            }
            linux {
                packageName = "openflux"
                menuGroup = "Network"
                appCategory = "Network"
                shortcut = true
            }
        }
    }
}

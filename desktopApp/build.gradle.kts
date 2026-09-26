import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.compose.components.resources)
}

compose.desktop {
    application {
        mainClass = "io.openflux.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg)
            packageName = "OpenFlux"
            packageVersion = "2.0.0"
            description = "OpenFlux desktop client"
            vendor = "meepo161"
            // Core binaries shipped next to the app (see core/README.md).
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))
            modules("java.net.http", "jdk.crypto.ec", "jdk.unsupported")

            windows {
                menuGroup = "OpenFlux"
                upgradeUuid = "3F0C7B52-9A2E-4C1B-8E77-5D2A6B1E9C40"
                shortcut = true
                perUserInstall = true
            }
        }
    }
}

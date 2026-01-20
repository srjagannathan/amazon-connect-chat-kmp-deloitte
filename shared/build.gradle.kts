import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.library")
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    js(IR) {
        browser {
            commonWebpackConfig {
                outputFileName = "shared.js"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = false
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material)
                api(compose.materialIconsExtended)
                api(compose.ui)
                implementation(compose.components.resources)

                // Ktor for HTTP client
                api("io.ktor:ktor-client-core:2.3.12")
                api("io.ktor:ktor-client-content-negotiation:2.3.12")
                api("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
                api("io.ktor:ktor-client-websockets:2.3.12")

                // Kotlinx
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }

        val androidMain by getting {
            dependencies {
                api("io.ktor:ktor-client-okhttp:2.3.12")
                api("androidx.activity:activity-compose:1.9.3")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("io.ktor:ktor-client-okhttp:2.3.12")
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.12")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:2.3.12")
            }
        }
    }
}

android {
    namespace = "com.amazon.connect.chat"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

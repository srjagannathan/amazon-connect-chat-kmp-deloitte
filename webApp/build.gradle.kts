plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    js(IR) {
        compilerOptions {
            moduleName = "webApp"
        }
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":shared"))
                @Suppress("DEPRECATION")
                implementation(compose.runtime)
                @Suppress("DEPRECATION")
                implementation(compose.ui)
                @Suppress("DEPRECATION")
                implementation(compose.foundation)
                @Suppress("DEPRECATION")
                implementation(compose.material)
            }
        }
    }
}

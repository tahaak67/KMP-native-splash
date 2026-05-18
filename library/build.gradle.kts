@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import com.android.build.api.dsl.androidLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "ly.com.tahaben"
version = libs.versions.kmpNativeSplashVersion.get()

kotlin {
    compilerOptions {
        // We use `expect/actual object` deliberately; suppress the in-beta warning.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    jvm()
    androidLibrary {
        namespace = "ly.com.tahaben.kmpnativesplash"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withJava() // enable java compilation support
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilations.configureEach {
            compilerOptions.configure {
                jvmTarget.set(
                    JvmTarget.JVM_11
                )
            }
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    linuxX64()
    // Web targets. The library publishes a klib per target so consumers with
    // js / wasmJs (Compose Multiplatform "web") resolve cleanly. nodejs() keeps the
    // library's own tests headless; the published klib is environment-agnostic, so
    // consumers using `browser()` are unaffected.
    js {
        nodejs()
    }
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            //put your multiplatform dependencies here
        }

        androidMain.dependencies {
            api(libs.androidx.core.splashscreen)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    signAllPublications()

    coordinates(group.toString(), "kmp-native-splash", version.toString())

    pom {
        name = "KMP-native-splash"
        description =
            "Generate native splash screens for Android and iOS from a single Kotlin Gradle DSL — and keep them on screen at runtime until your app is ready to draw."
        inceptionYear = "2026"
        url = "https://github.com/tahaak67/KMP-native-splash/"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "tahaak67"
                name = "Taha Banashur"
                url = "https://github.com/tahaak67"
                email = "dev@tahaben.com.ly"
                organization = "Taha Banashur"
                organizationUrl = "https://tahaben.com.ly"
            }
        }
        scm {
            url = "https://github.com/tahaak67/KMP-native-splash"
            connection = "scm:git:git://github.com/tahaak67/KMP-native-splash.git"
            developerConnection = "scm:git:ssh://github.com/tahaak67/KMP-native-splash.git"
        }
    }
}

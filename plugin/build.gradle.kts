import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("org.jetbrains.kotlin.jvm")
}

group = "ly.com.tahaben"
version = libs.versions.kmpNativeSplashVersion.get()
base { archivesName.set("kmp-native-splash-plugin") }
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin.api)
    compileOnly(libs.kotlin.gradle.plugin)
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("kmpNativeSplash") {
            id = "ly.com.tahaben.kmp-native-splash"
            implementationClass = "ly.com.tahaben.kmpnativesplash.KmpNativeSplashPlugin"
            displayName = "KMP Native Splash"
            description = "Generates native splash assets for Android and iOS in Kotlin Multiplatform projects."
        }
    }
}

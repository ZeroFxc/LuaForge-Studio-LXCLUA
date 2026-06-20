import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.luaforge.studio.lxclua"
    compileSdk = 36
    ndkVersion = "29.0.13004108"

    defaultConfig {
        applicationId = "com.luaforge.studio.lxclua"
        minSdk = 24
        targetSdk = 36
        versionCode = 20260620
        versionName = "1.1.4"

        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
        buildConfigField("String", "COPYRIGHT_YEAR", "\"${getCurrentYear()}\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            ndkBuild {
                arguments += listOf(
                    "NDK_APPLICATION_MK=src/main/jni/Application.mk",
                    "V=${if (gradle.startParameter.taskNames.any { it.contains("Release") }) "0" else "1"}"
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlin {
       compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    signingConfigs {
        getByName("debug") {
            keyAlias = "difierline"
            keyPassword = "difierline"
            storeFile = rootProject.file("difierline.jks")
            storePassword = "difierline"
        }
        create("release") {
            keyAlias = "difierline"
            keyPassword = "difierline"
            storeFile = rootProject.file("difierline.jks")
            storePassword = "difierline"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")

            externalNativeBuild {
                ndkBuild {
                    arguments += listOf("NDK_DEBUG=0")
                    cFlags += listOf("-DRELEASE_BUILD")
                    cppFlags += listOf("-DRELEASE_BUILD")
                }
            }
        }

        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            isJniDebuggable = true

            externalNativeBuild {
                ndkBuild {
                    arguments += listOf("NDK_DEBUG=1")
                    cFlags += listOf("-DDEBUG_BUILD")
                    cppFlags += listOf("-DDEBUG_BUILD")
                }
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "DebugProbesKt.bin",
                "**/*.proto",
                "**/kotlin/**",
                "**/*.version",
                "**/androidsupportmultidexversion.txt"
            )
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += listOf(
                "**/libc++_static.a",
                "**/*.a"
            )
        }
    }
}

// 工具函数
fun getBuildTime(): String = try {
    System.currentTimeMillis().toString()
} catch (e: Exception) {
    "1735651200000"
}

fun getCurrentYear(): String = LocalDate.now().year.toString()

// 复制 core.apk 到 assets
tasks.register<Copy>("copyCoreApkToAssets") {
    dependsOn(":core-apk:assembleRelease")

    from(project(":core-apk").layout.buildDirectory.file("outputs/apk/release/core-apk-release.apk"))
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "core.apk" }
}

tasks.named("preBuild") {
    dependsOn("copyCoreApkToAssets")
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Module Dependencies
    api(project(":editor"))
    api(project(":core"))
    api(project(":signer"))

    // Compose Core
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.ui.tooling.preview)
    api(libs.compose.material3)
    api(libs.compose.material3.window.size)
    api(libs.compose.material.icons.extended)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.animation.graphics)

    // AndroidX Core & Lifecycle
    api(libs.core.ktx)
    api(libs.core)
    api(libs.activity.compose)
    api(libs.lifecycle.runtime.ktx)
    api(libs.lifecycle.viewmodel.compose)

    // Navigation
    api(libs.navigation.compose)
    api(libs.navigation.common)
    api(libs.navigation.fragment)
    api(libs.navigation.runtime)
    api(libs.navigation.ui)

    // DataStore
    api(libs.datastore.preferences)

    // Material & Accompanist
    api(libs.material)
    api(libs.accompanist.permissions)

    // Third-party Libraries
    api(libs.compose.scrollbars) {
        exclude(group = "androidx.compose", module = "compose-bom")
    }
    api(libs.coil.compose)
    api(libs.gson)
    api(libs.ktoast)
    
    api("org.eclipse.jdt:ecj:3.33.0")
    api("com.android.tools:r8:8.2.42")
    api("io.github.kyant0:backdrop-android:2.0.0-alpha01")

    // MCP Kotlin SDK
    api(libs.mcp.kotlin.sdk)
    api(libs.kotlinx.serialization.json)

}

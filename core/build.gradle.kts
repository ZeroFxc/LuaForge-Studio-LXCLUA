plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.luaforge.studio.lxclua.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isShrinkResources = false

            ndk {
                abiFilters.addAll(listOf("arm64-v8a"))
            }
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false

            ndk {
                abiFilters.addAll(listOf("arm64-v8a"))
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // KSP 注解依赖（SOURCE 保留，不打入 APK）
    compileOnly(project(":annotations"))

    // Compose Runtime（供 luacompose 桥接使用）
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material.icons.extended)

    // Navigation
    api(libs.navigation.fragment)
    api(libs.navigation.ui)

    // Navigation3 (Compose 原生导航)
    api(libs.navigation3.runtime)
    api(libs.navigation3.ui)

    // Material Design
    api(libs.material)

    // AndroidX Misc
    api(libs.activity)
    api(libs.activity.compose)
    api(libs.appcompat)
    api(libs.annotation)
    api(libs.collection)
    api(libs.constraintlayout)
    api(libs.coordinatorlayout)
    api(libs.customview)
    api(libs.documentfile)
    api(libs.drawerlayout)
    api(libs.dynamicanimation)
    api(libs.fragment)
    api(libs.gridlayout)
    api(libs.legacy.support.core.ui)
    api(libs.legacy.support.core.utils)
    api(libs.localbroadcastmanager)
    api(libs.palette)
    api(libs.preference)
    api(libs.startup.runtime)
    api(libs.swiperefreshlayout)
    api(libs.slidingpanelayout)
    api(libs.recyclerview)
    api(libs.transition)
    api(libs.window)
    api(libs.viewpager)
    api(libs.viewpager2)
    api(libs.cardview)
    api(libs.browser)

    // Networking & Parsing
    api(libs.gson)

    // Image Loading (Glide)
    api(libs.glide)
    api(libs.okhttp3.integration)

    // HTTP Client (OkHttp)
    api(libs.okhttp)

    // KSP 处理器
    ksp(project(":compiler"))
}
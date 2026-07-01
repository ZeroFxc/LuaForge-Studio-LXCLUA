import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.luaforge.studio.lxclua.core"
    compileSdk = 36
    ndkVersion = "29.0.13004108"

    defaultConfig {
        applicationId = "com.luaforge.studio.lxclua.core"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    /*sourceSets {
        getByName("main") {
            if (project.rootProject.file("app/src/main/jniLibs").exists()) {
                jniLibs.srcDirs(project.rootProject.file("app/src/main/jniLibs"))
            }
        }
    }*/

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        getByName("debug") {
            keyAlias = "luaappxcore"
            keyPassword = "luaappxcore"
            storeFile = rootProject.file("debug.keystore")
            storePassword = "luaappxcore"
        }
        create("release") {
            keyAlias = "luaappxcore"
            keyPassword = "luaappxcore"
            storeFile = rootProject.file("debug.keystore")
            storePassword = "luaappxcore"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isShrinkResources = false
            ndk {
                abiFilters.addAll(listOf("arm64-v8a"))
            }
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            ndk {
                abiFilters.addAll(listOf("arm64-v8a"))
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("../app/src/main/jni/Android.mk")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// == 编译策略：Java/Kotlin 强制重编，NDK 保留增量 ==
// core-apk 是导出 APK 的核心载体，源码变更后必须重新构建
// Kotlin/Java 编译走增量（只编译变更文件），但禁用构建缓存避免过期
// NDK（C/C++）耗时较长，保留 Gradle 原生增量检测，仅 C 文件变更时触发
afterEvaluate {
    tasks.matching {
        it.name.startsWith("compile") &&
        (it.name.contains("Kotlin") || it.name.contains("JavaWithJavac"))
    }.configureEach {
        outputs.cacheIf { false }
    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    api(project(":core"))

    // Compose 运行时（必须显式依赖，否则导出 APK 中 Compose 不会初始化）
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.material3)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material.icons.extended)
    api(libs.activity.compose)
    api(libs.lifecycle.runtime.ktx)
    api(libs.lifecycle.viewmodel.compose)
    api(libs.navigation.compose)

    // Navigation
    api(libs.navigation.fragment)
    api(libs.navigation.ui)

    // Material Design
    api(libs.material)

    // 核心UI组件
    api(libs.activity)
    api(libs.appcompat)
    api(libs.fragment)
    api(libs.constraintlayout)
    api(libs.recyclerview)
    api(libs.viewpager2)
    api(libs.coordinatorlayout)
    api(libs.swiperefreshlayout)

    // 实用组件
    api(libs.preference)
    api(libs.drawerlayout)
    api(libs.transition)

    // 网络和图片
    api(libs.gson)
    api(libs.glide)
    api(libs.okhttp)
    api(libs.okhttp3.integration)
}
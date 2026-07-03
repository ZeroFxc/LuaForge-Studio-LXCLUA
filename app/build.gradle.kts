import java.time.LocalDate

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.luaforge.studio.lxclua"
    compileSdk = 36
    ndkVersion = "29.0.13004108"

    defaultConfig {
        applicationId = "com.luaforge.studio.lxclua"
        minSdk = 24
        targetSdk = 35
        versionCode = 20260704
        versionName = "1.2.5"

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
// 源码追踪策略：
//   - core/core-apk 的 Java/Kotlin 源码变更 → 强制重新编译并复制（增量编译，很快）
//   - JNI C 源码变更 → 触发复制，但 NDK 编译由 externalNativeBuild 自身增量检测控制（耗时）
tasks.register<Copy>("copyCoreApkToAssets") {
    dependsOn(":core-apk:assembleRelease")

    // 声明 core 模块所有源码为输入，任何源码变动都会使本任务过期
    inputs.dir(project(":core").layout.projectDirectory.dir("src/main/java"))
        .withPropertyName("coreSrcJava")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    val coreKotlinSrc = project(":core").layout.projectDirectory.dir("src/main/kotlin")
    if (coreKotlinSrc.asFile.exists()) {
        inputs.dir(coreKotlinSrc)
            .withPropertyName("coreSrcKotlin")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    // 声明 core-apk 模块自己的源码为输入
    val coreApkJavaSrc = project(":core-apk").layout.projectDirectory.dir("src/main/java")
    if (coreApkJavaSrc.asFile.exists()) {
        inputs.dir(coreApkJavaSrc)
            .withPropertyName("coreApkSrcJava")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }
    val coreApkKotlinSrc = project(":core-apk").layout.projectDirectory.dir("src/main/kotlin")
    if (coreApkKotlinSrc.asFile.exists()) {
        inputs.dir(coreApkKotlinSrc)
            .withPropertyName("coreApkSrcKotlin")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    // 声明 JNI 源码目录为输入（C 文件变更时触发复制，但 NDK 编译由自身增量控制）
    val jniDir = rootProject.file("app/src/main/jni")
    if (jniDir.exists()) {
        inputs.dir(jniDir)
            .withPropertyName("jniSrc")
            .withPathSensitivity(PathSensitivity.RELATIVE)
    }

    from(project(":core-apk").layout.buildDirectory.file("intermediates/apk/release/core-apk-release.apk"))
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
    
    api("org.eclipse.jdt:ecj:3.46.0")
    api("com.android.tools:r8:9.1.31")
    api("io.github.kyant0:backdrop-android:2.0.0-alpha01")

    // MCP Kotlin SDK
    api(libs.mcp.kotlin.sdk)
    api(libs.kotlinx.serialization.json)

    
    // 官方 MCP SDK
    implementation("io.modelcontextprotocol:kotlin-sdk:0.13.0")
    
    // Ktor 依赖，用于 MCP SDK
    implementation("io.ktor:ktor-client-core:3.5.0")
    implementation("io.ktor:ktor-client-okhttp:3.5.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")

}

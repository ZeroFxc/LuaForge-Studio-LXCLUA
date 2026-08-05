plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

android {
    namespace = "io.github.kulipai.luahook.androlua"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)

    api(fileTree("libs"))

}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.kulipai.luacompose"
                artifactId = "androlua"
                version = "1.0.0-SNAPSHOT"
            }
        }
    }
}

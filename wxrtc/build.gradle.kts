plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.wx.rtc"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 模块混淆配置
        consumerProguardFiles("consumer-rules.pr")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    sourceSets["main"].jniLibs.srcDir("libs")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    publishing {
        singleVariant("release") {
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.annotation.jvm)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.luban)
}


afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("product") {
                from(components["release"])
                groupId = "com.github.xieejianglin"
                artifactId = "wxrtc"
                version = "0.0.3"
            }
        }
    }
}
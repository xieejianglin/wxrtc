import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization.plugin)
    id("maven-publish")
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
//    iosX64()
    iosArm64()
//    iosSimulatorArm64()

    cocoapods {
        summary = "Some description for the Shared Module"
        homepage = "Link to the Shared Module homepage"
        version = "1.0"
        ios.deploymentTarget = "13.0"
        framework {
            baseName = "wxrtc"
            isStatic = true
        }
    }
    
    sourceSets {
        val commonMain by getting

        commonMain.dependencies {
            //put your multiplatform dependencies here
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.core)
            implementation(libs.ktor.content.negotiation)
            implementation(libs.ktor.json)
            implementation(libs.ktor.websocket)
        }
//        commonTest.dependencies {
//            implementation(libs.kotlin.test)
//        }
        androidMain.dependencies {
            implementation(libs.androidx.annotation)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.okhttp)
            implementation(libs.ktor.okhttp)
        }
        val nonAndroidMain by creating {
            dependsOn(commonMain) // 继承 commonMain 的内容
            dependencies {
                // 添加非 Android 平台所需的依赖，例如：
                implementation(libs.kotlinx.coroutines.core) // 若需要协程支持
            }
        }
//        val iosMain by getting(iosMain) {
//            dependsOn(nonAndroidMain)
//            dependencies {
//                implementation(libs.ktor.darwin)
//            }
//        }
        iosMain.dependencies {
            implementation(libs.ktor.darwin)
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

android {
    namespace = "com.wx.rtc"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pr")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets["main"].jniLibs.srcDir("libs")
    sourceSets["main"].java.srcDirs("src/androidMain/java")
    publishing {
        singleVariant("release") {
        }
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("product") {
                from(components["release"])
                groupId = "com.github.xieejianglin"
                artifactId = "wxrtc"
                version = libs.versions.versionName.get()
            }
        }
    }
}
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://maven-central-asia.storage-download.googleapis.com/maven2/") }
        maven { setUrl("https://maven.aliyun.com/repository/google") }
        maven { setUrl("https://maven.aliyun.com/repository/public") }
        maven {
            setUrl("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github.*")
            }
        }
    }
}

rootProject.name = "wxrtctest"
include(":androidApp")
include(":shared")
include(":wxrtc")
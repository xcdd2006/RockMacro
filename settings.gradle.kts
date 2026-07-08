pluginManagement {
    val useAliyunRepo = System.getenv("USE_ALIYUN_REPO")?.toBoolean() ?: true
    repositories {
        if (useAliyunRepo) {
            maven { setUrl("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { setUrl("https://maven.aliyun.com/repository/google") }
            maven { setUrl("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    val useAliyunRepo = System.getenv("USE_ALIYUN_REPO")?.toBoolean() ?: true
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useAliyunRepo) {
            maven { setUrl("https://maven.aliyun.com/repository/google") }
            maven { setUrl("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "rockmacro"
include(":app")
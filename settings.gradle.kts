pluginManagement {
    repositories {
        // 本机对 dl.google.com / repo1.maven.org 的 DNS 被黑洞（解析到 fdfe:dcba:9876::），
        // 直连超时，优先走阿里云代理命中缓存或镜像坐标。
        // google() / mavenCentral() 在后作为 CI 兜底：GitHub runner 直连可达，
        // 且部分坐标（如 com.google.devtools.ksp 的 Gradle plugin marker）阿里云未镜像。
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "逆向系老挂"
include(":app")

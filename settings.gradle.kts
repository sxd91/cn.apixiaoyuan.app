pluginManagement {
    repositories {
        // 顺序关键：CI（GitHub runner）直连 maven.google.com / repo1.maven.org 可达，
        // 必须排在阿里云镜像之前。阿里云对 KSP 的 Gradle plugin marker
        // （com.google.devtools.ksp.gradle.plugin:2.3.12）返回的不是干净的 404，
        // Gradle 拿到非 404 响应就认定该仓库「有这个 artifact」，随后 POM 解析失败，
        // 直接抛 could not resolve plugin artifact 并中止，不再尝试后续仓库。
        // 本机对这两个官方域名 DNS 被黑洞，但本地 Gradle 缓存已含 KSP 2.3.12 全套，
        // 命中缓存不走网络，编译不受影响。
        google()
        mavenCentral()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
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

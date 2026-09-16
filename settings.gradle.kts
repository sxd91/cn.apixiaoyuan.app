pluginManagement {
    repositories {
        // 本机对 dl.google.com / repo1.maven.org 的 DNS 被黑洞（解析到 fdfe:dcba:9876::），
        // 直连必然超时。全部改走阿里云代理，顺序：google 代理 -> public 聚合 -> gradle plugin portal。
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
    }
}

rootProject.name = "逆向系老挂"
include(":app")

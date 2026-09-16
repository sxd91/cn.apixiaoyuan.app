import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // AGP 9.0 起内置 Kotlin 支持，org.jetbrains.kotlin.android 插件不再应用，
    // 否则 apply 阶段直接报 "no longer required for Kotlin support since AGP 9.0"。
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // KSP 暂不引入：当前工程无 @Entity/@Dao，注解处理器空转。数据层开写时补回。
}

fun gitShortHash(): String = providers.exec {
    commandLine("git", "rev-parse", "--short=8", "HEAD")
}.standardOutput.asText.get().trim()

val versionProps = Properties().apply {
    val f = file("../version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "cn.apixiaoyuan.app"
    compileSdk = 37

    signingConfigs {
        val jks = file("../keystore.jks")
        if (jks.exists()) {
            register("release") {
                storeFile = jks
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "cn.apixiaoyuan.app"
        minSdk = 33
        targetSdk = 37
        versionCode = (project.findProperty("versionCode") as String? ?: versionProps.getProperty("versionCode", "1")).toInt()
        versionName = project.findProperty("versionName") as String? ?: versionProps.getProperty("versionName", "0.1.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: getByName("debug").signingConfig
            versionNameSuffix = runCatching { "-${gitShortHash()}" }.getOrNull() ?: ""
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // --- miuix（LiquidGlass 悬浮底栏 / shader / nav） ---
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.nav)

    // --- MaterialSymbols 图标库 ---
    // 只引 outlined：filled 变体本地缓存无该产物、包结构未经解包验证，
    // AppIcons 统一复用 outlined。等 CI 跑通后再补 filled 与 forKeySelected。

    // --- material-kolor（莫奈取色） ---
    implementation(libs.materialkolor)

    // --- kotlinx-serialization ---
    implementation(libs.kotlinx.serialization.json)

    // --- Room（数据层开写时连同 KSP 一起加回） ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)

    // --- 网络 ---
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
}

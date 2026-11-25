plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "io.github.univers629.millet"
    compileSdk = 37
    // miuix 0.9.5-beta04 的 AAR 元数据要求 minCompileSdk=37 + minCompileMinorSdk=2，AGP 9.4 起会校验次版本
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "io.github.univers629.millet"
        minSdk = 33
        targetSdk = 37
        versionCode = 1300
        versionName = "1.3.0"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // 发布签名：CI 通过环境变量提供 PKCS12 密钥库（来自仓库 Secrets），本地无密钥时 release 产物保持未签名
    val releaseKeystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    val hasReleaseKeystore = !releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).exists()
    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(releaseKeystorePath!!)
                storeType = "PKCS12"
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_STORE_PASSWORD")
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    // 现代 LSPosed / LibXposed API (102.0.0，由框架注入运行时)
    compileOnly("io.github.libxposed:api:102.0.0")

    // LibXposed 服务端：让 LSPosed 能把本应用的设置以 RemotePreferences 递给 Hook 进程
    implementation("io.github.libxposed:service:102.0.0")

    // Jetpack Compose & Activity & Lifecycle
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.navigationevent:navigationevent:1.2.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.2.0")

    // Miuix KMP UI 组件库、图标、导航、设置项与毛玻璃特效 (0.9.5-beta04)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.5-beta04")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.5-beta04")
    implementation("top.yukonga.miuix.kmp:miuix-glass-icons-android:0.9.5-beta04")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:0.9.5-beta04")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.5-beta04")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.5-beta04")
    implementation("top.yukonga.miuix.kmp:miuix-material-android:0.9.5-beta04")
    implementation("top.yukonga.miuix.kmp:miuix-glass-android:0.9.5-beta04")

    // 返回栈持久化（rememberNavBackStack 的 JSON 编解码）使用
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Shizuku / Sui：模块未生效时的降级特权通道（adb 或 root 身份执行系统命令）
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    // 预测性返回开关需要反射 ApplicationInfo 的隐藏 API
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}


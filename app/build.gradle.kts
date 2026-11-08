// 차량 앱 화면 (Compose). 로직은 :vehicle-core 가 담당한다.
// APK에는 여기 dependencies 에 적은 것(+ 그것들이 의존하는 것)만 들어간다.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.autolog.vehicle"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.autolog.vehicle"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    buildTypes {
        release {
            // APK 크기: R8로 안 쓰는 코드 제거
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
}

dependencies {
    implementation(project(":vehicle-core")) // 차량 로직 (→ :shared 포함). :relay 는 절대 넣지 않는다.

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.m3)
    implementation(libs.compose.tooling.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.vm.compose)
    implementation(libs.lifecycle.runtime.compose)

    testImplementation(libs.junit)
}

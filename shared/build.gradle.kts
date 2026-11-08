// 앱과 서버가 함께 쓰는 inbox payload DTO.
// ⚠️ 이 모듈은 APK에 들어간다. kotlinx-serialization 외 의존성(Spring, JPA 등)을 추가하지 말 것.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(libs.kotlinx.serialization)
    testImplementation(libs.junit)
}

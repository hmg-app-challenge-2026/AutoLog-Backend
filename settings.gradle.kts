pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google(); mavenCentral()
        maven("https://nexus-playground.pleos.ai/repository/maven-releases/")   // Pleos SDK
    }
}
rootProject.name = "AutoLog"

// 저장소에 있는 Gradle 모듈 목록일 뿐, APK 포함 여부와는 무관하다.
// APK에 무엇이 들어가는지는 app/build.gradle.kts 의 dependencies 가 결정한다.
include(":shared", ":vehicle-core", ":app", ":relay")

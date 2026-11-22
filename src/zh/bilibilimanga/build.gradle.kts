import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

dependencies {
    implementation(project(":lib:ece"))

    testImplementation(libs.bundles.common)
    testImplementation(libs.junit)
}

keiyoushi {
    name = "BILIBILI MANGA"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "哔哩哔哩漫画"
        baseUrl = "https://manga.bilibili.com"
        lang = "zh"
    }

    deeplink {
        path("/detail/mc..*")
        path("/mc..*")
        path("/m/detail/mc..*")
        path("/m/mc..*")
    }
}

// Unit tests, not built in CI: ./gradlew :src:zh:bilibilimanga:testDebugUnitTest
android {
    sourceSets.named("test") {
        kotlin.directories.add("test")
    }
}

// The @Source processor only applies to the main source set.
tasks.matching { it.name.startsWith("ksp") && it.name.contains("UnitTest") }.configureEach {
    enabled = false
}

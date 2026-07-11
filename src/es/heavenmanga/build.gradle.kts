import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HeavenManga"
    versionCode = 9
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://heavenmanga.com"
    }
}

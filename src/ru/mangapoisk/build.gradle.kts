import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaPoisk"
    versionCode = 16
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://mangapsk.ru"
    }
}

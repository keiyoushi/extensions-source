import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AComics"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://acomics.ru"
    }
}

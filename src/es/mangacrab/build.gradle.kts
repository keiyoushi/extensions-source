import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Crab"
    versionCode = 26
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://es.mangacrab.org"
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaPoisk"
    versionCode = 16
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl {
            custom("https://mangapoisk.me")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga-shi"
    versionCode = 52
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ru"
        baseUrl = "https://manga-shi.org"
    }

    deeplink {
        host("manga-shi.org")
        path("/manga/..*")
    }
}

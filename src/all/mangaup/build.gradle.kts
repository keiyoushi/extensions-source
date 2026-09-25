import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga UP!"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://global.manga-up.com"
    }

    deeplink {
        host("global.manga-up.com")
        host("www.global.manga-up.com")
        path("/manga/..*")
    }
}

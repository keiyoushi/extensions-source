import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Madokami"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://manga.madokami.al"
    }

    deeplink {
        path("/Manga/..*")
        path("/Raws/..*")
        path("/Artbooks/..*")
        path("/reader/..*")
    }
}

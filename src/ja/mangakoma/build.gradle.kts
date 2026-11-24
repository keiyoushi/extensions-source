import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Koma"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "liliana"

    source {
        lang = "ja"
        baseUrl = "https://mangakoma01.top"
    }

    deeplink {
        path("/manga/..*")
    }
}

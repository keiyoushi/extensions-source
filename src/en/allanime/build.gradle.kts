import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AllManga"
    versionCode = 22
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mkissa.to"
        id = 4709139914729853090L
    }

    deeplink {
        host("mkissa.to")
        host("allmanga.to")
        path("/manga/..*")
        path("/read/..*")
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AllManga"
    versionCode = 20
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

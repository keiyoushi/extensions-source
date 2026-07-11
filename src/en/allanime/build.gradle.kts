plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AllManga"
    versionCode = 19
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://allmanga.to"
        id = 4709139914729853090L
    }

    deeplink {
        host("allmanga.to")
        path("/manga/..*")
        path("/read/..*")
    }
}

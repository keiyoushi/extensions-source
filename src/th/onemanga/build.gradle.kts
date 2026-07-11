plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaBlackCat"
    versionCode = 33
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "th"
        baseUrl = "https://mangablackcat.com"
        id = 2248402620929558947L
    }
}

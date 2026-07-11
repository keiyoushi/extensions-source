plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaYY"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://mangayy.org"
        id = 828698548689586603L
    }
}

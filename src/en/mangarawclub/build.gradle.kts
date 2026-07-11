plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaGeko"
    versionCode = 32
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.mgeko.cc"
        id = 734865402529567092L
    }
}

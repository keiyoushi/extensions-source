plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SoaiCaComic"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://soaicacomic2.top"
    }
}

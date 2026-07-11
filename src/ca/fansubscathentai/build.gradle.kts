plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai.cat"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "fansubscat"

    source {
        lang = "ca"
        baseUrl = "https://manga.hentai.cat"
        id = 7575385310756416449L
    }
}

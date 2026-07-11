plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Tokyo"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "gattsu"

    source {
        lang = "pt-BR"
        baseUrl = "https://hentaitokyo.net"
    }
}

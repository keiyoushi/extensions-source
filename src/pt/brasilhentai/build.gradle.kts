plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Brasil Hentai"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://brasilhentai.com"
    }

    deeplink {
        host("brasilhentai.com")
        path("/..*")
    }
}

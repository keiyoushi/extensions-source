plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiPill"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://hentaipill.com"
    }

    deeplink {
        path("/gallery/..*")
    }
}

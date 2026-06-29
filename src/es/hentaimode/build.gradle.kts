plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiMode"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://hentaimode.com"
    }

    deeplink {
        host("hentaimode.com")
        path("/g/..*")
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZettaHQ"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://zettahq.com"
    }

    deeplink {
        host("zettahq.com")
        path("/..*")
    }
}

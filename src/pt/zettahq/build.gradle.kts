plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZettaHQ"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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

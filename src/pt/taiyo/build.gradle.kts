plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Taiyō"
    versionCode = 11
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://taiyo.moe"
    }

    deeplink {
        host("taiyo.moe")
        path("/media/..*")
    }
}

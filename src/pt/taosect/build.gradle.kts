plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tao Sect"
    versionCode = 22
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://taosect.com"
    }

    deeplink {
        host("taosect.com")
        path("/projeto/..*")
    }
}

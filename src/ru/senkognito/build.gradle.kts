plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Senkognito"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "senkuro"

    source {
        baseUrl {
            mirrors(
                "Россия" to "https://senkuro.me",
                "Публичный" to "https://senkognito.com",
            )
        }
        lang = "ru"
    }
}

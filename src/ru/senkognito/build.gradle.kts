plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Senkognito"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
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

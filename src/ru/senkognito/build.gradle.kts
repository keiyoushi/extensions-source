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
        baseUrl("https://senkuro.me") {
            mirrors = listOf(
                "https://senkognito.com",
            )
        }
        lang = "ru"
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Senkuro"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "senkuro"

    source {
        baseUrl("https://senkuro.me") {
            mirrors = listOf(
                "https://senkuro.com",
            )
        }
        lang = "ru"
    }
}

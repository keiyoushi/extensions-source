plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwasMe"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manhwas.me"
        id = 8004442288770923365L
    }
}

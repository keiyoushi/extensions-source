plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwasMe"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://manhwas.me"
        id = 8004442288770923365L
    }
}

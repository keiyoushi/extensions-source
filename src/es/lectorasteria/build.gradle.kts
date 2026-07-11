plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lector Asteria"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "moonlighttl"

    source {
        lang = "es"
        baseUrl = "https://lectorasteria.com"
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Erofus"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "eromuse"

    source {
        lang = "en"
        baseUrl = "https://www.erofus.com"
    }
}

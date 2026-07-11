plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yaoi Flix"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://yaoiflix.fit"
    }
}

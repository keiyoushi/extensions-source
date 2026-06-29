plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yaoi Flix"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "tr"
        baseUrl = "https://yaoiflix.fit"
    }
}

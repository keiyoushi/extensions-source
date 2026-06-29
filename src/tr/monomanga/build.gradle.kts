plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mono Manga"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://monomanga.com.tr"
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mono Manga"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://monomanga.com.tr"
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Saison"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://mechacomi.jp"
    }
}

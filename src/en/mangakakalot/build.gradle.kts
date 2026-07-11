plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakakalot"
    versionCode = 8
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://www.mangakakalot.gg",
                "https://www.mangakakalove.com",
            )
        }
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kiryuu"
    versionCode = 51
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "natsuid"

    source {
        lang = "id"
        baseUrl = "https://v6.kiryuu.to"
        // Formerly "Kiryuu (WP Manga Stream)"
        id = 3639673976007021338L
    }
}

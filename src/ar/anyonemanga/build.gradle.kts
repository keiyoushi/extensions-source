plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Anyone Manga"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "ar"
        baseUrl = "https://anyonemanga.com"
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Uzay Manga"
    versionCode = 42
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "uzaymanga"

    source {
        lang = "tr"
        baseUrl = "https://uzaymanga.com"
        versionId = 3
    }
}

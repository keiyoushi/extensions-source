plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Tokyo Ghoul Re & Tokyo Ghoul Manga Online"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww11.tokyoghoulre.com"
    }
}

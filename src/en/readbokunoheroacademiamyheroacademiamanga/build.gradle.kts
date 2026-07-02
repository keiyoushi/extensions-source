plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Boku no Hero Academia My Hero Academia Manga"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww10.readmha.com"
    }
}

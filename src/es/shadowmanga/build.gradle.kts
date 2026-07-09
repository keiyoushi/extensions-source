plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shadow Manga"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://shademanga.com"
    }
}

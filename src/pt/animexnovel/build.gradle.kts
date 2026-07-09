plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AnimeXNovel"
    versionCode = 18
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://www.animexnovel.com"
        versionId = 2
    }
}

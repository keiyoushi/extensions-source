plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaNo"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://manga-no.com"
    }
}

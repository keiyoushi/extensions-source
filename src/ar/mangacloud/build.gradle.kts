plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaCloud"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ar"
        baseUrl = "https://mangacloud.online"
    }
}

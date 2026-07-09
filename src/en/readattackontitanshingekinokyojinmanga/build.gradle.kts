plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Read Attack on Titan Shingeki no Kyojin Manga"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangacatalog"

    source {
        lang = "en"
        baseUrl = "https://ww11.readsnk.com"
    }
}

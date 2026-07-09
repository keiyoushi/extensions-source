plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadAllComics"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://readallcomics.com"
    }
}

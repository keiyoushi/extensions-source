plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ApollComics"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://apollcomics.es"
    }
}

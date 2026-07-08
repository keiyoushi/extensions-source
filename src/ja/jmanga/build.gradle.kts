plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Jmanga"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangareader"

    source {
        lang = "ja"
        baseUrl {
            custom("https://jmanga.codes")
        }
    }
}

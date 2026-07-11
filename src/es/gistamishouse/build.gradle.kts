plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Gistamis House"
    versionCode = 2
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "es"
        baseUrl = "https://gistamishousefansub.blogspot.com"
    }
}

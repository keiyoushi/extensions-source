plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yokai"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "ar"
        baseUrl = "https://yokai-team.blogspot.com"
    }
}

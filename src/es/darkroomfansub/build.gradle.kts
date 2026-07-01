plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dark Room Fansub"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "es"
        baseUrl = "https://lector-darkroomfansub.blogspot.com"
    }
}

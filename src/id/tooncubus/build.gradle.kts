plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tooncubus"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "zeistmanga"

    source {
        lang = "id"
        baseUrl = "https://www.tooncubus.top"
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga 18x"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://manga18x.net"
    }
}

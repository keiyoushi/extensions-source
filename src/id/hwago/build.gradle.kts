plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hwago"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "id"
        baseUrl = "https://01.hwago.xyz"
    }
}

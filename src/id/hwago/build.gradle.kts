plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hwago"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "id"
        baseUrl = "https://01.hwago.xyz"
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TopManhua.fan"
    versionCode = 0
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://www.topmanhua.fan"
    }
}

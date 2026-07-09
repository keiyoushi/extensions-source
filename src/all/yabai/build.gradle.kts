plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yabai"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://yabai.si"
    }
}

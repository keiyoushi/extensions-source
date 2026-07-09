plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mokuro"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://mokuro.moe"
    }
}

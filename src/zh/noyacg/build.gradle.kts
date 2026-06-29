plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NoyAcg"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://beta.noyteam.online"
    }
}

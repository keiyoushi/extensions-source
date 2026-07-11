plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NoyAcg"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://beta.noyteam.online"
    }
}

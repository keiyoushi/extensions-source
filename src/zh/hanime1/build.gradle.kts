plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hanime1"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "zh"
        name = "Hanime1.me"
        baseUrl = "https://hanimeone.me"
    }
}

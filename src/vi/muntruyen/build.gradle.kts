plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MunTruyen"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://moonnovel.store") {
            withCustom = true
        }
    }
}

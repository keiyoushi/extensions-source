plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yidan Girl"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "一耽女孩"
        lang = "zh"
        baseUrl("https://yidan9.club") {
            withCustom = true
        }
    }
}

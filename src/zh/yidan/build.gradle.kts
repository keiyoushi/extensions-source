plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yidan Girl"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        name = "一耽女孩"
        lang = "zh"
        baseUrl {
            custom("https://yidan9.club")
        }
    }
}

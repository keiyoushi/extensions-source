plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CosplayTele"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://cosplaytele.com"
    }
}

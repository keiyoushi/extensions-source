plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiHot"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://yaoihot.com"
    }
}

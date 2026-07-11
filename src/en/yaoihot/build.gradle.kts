plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "YaoiHot"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://yaoihot.com"
    }
}

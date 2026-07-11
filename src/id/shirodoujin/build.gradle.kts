plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shiro Doujin"
    versionCode = 1
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "zmanga"

    source {
        lang = "id"
        baseUrl = "https://shirodoujin.com"
    }
}

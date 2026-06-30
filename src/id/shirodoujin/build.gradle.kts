plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Shiro Doujin"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "zmanga"

    source {
        lang = "id"
        baseUrl = "https://shirodoujin.com"
    }
}

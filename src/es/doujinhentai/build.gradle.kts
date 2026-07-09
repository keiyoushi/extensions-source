plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DoujinHentai"
    versionCode = 50
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://doujinhentai.net"
    }
}

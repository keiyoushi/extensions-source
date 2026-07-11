plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DoujinHentai"
    versionCode = 50
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://doujinhentai.net"
    }
}

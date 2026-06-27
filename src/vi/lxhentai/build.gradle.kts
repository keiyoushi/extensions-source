plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LXManga"
    className = "LxHentai"
    versionCode = 32
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("lxmanga.space")
        path("/truyen/..*")
    }
}

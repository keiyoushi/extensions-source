plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LXManga"
    className = "LxHentai"
    versionCode = 32
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        id = 6495630445796108150L
        lang = "vi"
        baseUrl("https://lxmanga.space") {
            withCustom = true
        }
    }

    deeplink {
        host("lxmanga.space")
        path("/truyen/..*")
    }
}

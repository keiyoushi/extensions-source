plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LXManga"
    versionCode = 32
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://lxmanga.space") {
            withCustom = true
        }
        id = 6495630445796108150
    }

    deeplink {
        host("lxmanga.space")
        path("/truyen/..*")
    }
}

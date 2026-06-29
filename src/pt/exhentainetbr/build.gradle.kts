plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ExHentai.net.br"
    className = "ExHentaiNetBR"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("exhentai.net.br")
        path("/manga/..*")
    }
}

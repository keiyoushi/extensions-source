plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ExHentai.net.br"
    versionCode = 4
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "pt-BR"
        baseUrl = "https://exhentai.net.br"
    }

    deeplink {
        host("exhentai.net.br")
        path("/manga/..*")
    }
}

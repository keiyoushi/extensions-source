import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ExHentai.net.br"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://exhentai.net.br"
    }

    deeplink {
        host("exhentai.net.br")
        path("/manga/..*")
    }
}

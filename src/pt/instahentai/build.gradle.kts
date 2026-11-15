import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Instahentai"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://instahentai.com"
        lang = "pt-BR"
    }

    deeplink {
        path("/serie/..*")
        path("/ler/..*")
    }
}

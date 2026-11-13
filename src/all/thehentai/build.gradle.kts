import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "The Hentai"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://thehentai.net"
    }

    source {
        lang = "en"
        baseUrl = "https://en.thehentai.net"
    }

    deeplink {
        path("/..*")
    }
}

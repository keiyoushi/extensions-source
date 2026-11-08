import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Neko Hentai"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://neko-hentai.net"
    }

    deeplink {
        path("/..*")
    }
}

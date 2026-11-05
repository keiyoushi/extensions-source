import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai Zone"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "it"
        baseUrl = "https://hentaizone.me"
    }

    deeplink {
        path("/..*")
    }
}

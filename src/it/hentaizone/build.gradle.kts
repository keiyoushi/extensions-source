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
        name = "Hentai Zone"
        lang = "it"
        baseUrl = "https://hentaizone.me"
        id = 7864123905182736401L
    }

    deeplink {
        path("/..*")
    }
}

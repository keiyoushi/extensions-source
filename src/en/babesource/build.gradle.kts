import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BabeSource"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://babesource.com"
    }

    deeplink {
        path("/galleries/..*")
        path("/pornstars/..*")
        path("/paysites/..*")
        path("/channels/..*")
        path("/search/..*")
    }
}

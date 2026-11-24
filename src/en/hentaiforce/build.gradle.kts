import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiForce"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://hentaiforce.net"
    }

    deeplink {
        path("/view/..*")
        path("/tag/..*")
        path("/artist/..*")
        path("/parody/..*")
        path("/character/..*")
        path("/language/..*")
        path("/category/..*")
    }
}

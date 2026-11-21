import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiFull"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://hentaifull.net"
    }

    deeplink {
        path("/hentai/..*")
        path("/tag/..*")
        path("/artist/..*")
        path("/group/..*")
        path("/category/..*")
    }
}

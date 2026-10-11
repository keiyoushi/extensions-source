import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiHere"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://hentaihere.com"
    }

    deeplink {
        host("hentaihere.com")
        path("/doujinshi/..*")
        path("/original/..*")
        path("/imageset/..*")
        path("/m/..*")
    }
}

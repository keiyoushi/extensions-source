import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NakedPics"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://hotnakedwomen.com"
    }

    deeplink {
        path("/gals/..*")
        path("/models/..*")
        path("/channels/..*")
        path("/tags/..*")
        path("/search/..*")
        path("/../gals/..*")
        path("/../models/..*")
        path("/../channels/..*")
        path("/../tags/..*")
        path("/../search/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Simply Cosplay"
    versionCode = 4
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://www.simply-cosplay.com"
    }

    deeplink {
        host("www.simply-cosplay.com")
        host("simply-cosplay.com")
        path("/gallery/..*")
        path("/image/..*")
    }
}

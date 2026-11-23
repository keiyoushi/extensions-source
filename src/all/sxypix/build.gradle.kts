import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SxyPix"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://www.sxypix.com"
    }

    deeplink {
        host("www.sxypix.com")
        host("sxypix.com")
        path("/w/..*")
        path("/s/..*")
        path("/u/..*")
        path("/x/..*")
    }
}

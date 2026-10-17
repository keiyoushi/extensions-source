import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "4KHD"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    source {
        lang = "all"
        baseUrl = "https://www.4khd.com"
    }

    deeplink {
        host("4khd.com")
        host("www.4khd.com")
        path("/content/..*")
    }
}

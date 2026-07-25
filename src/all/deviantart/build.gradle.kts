import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DeviantArt"
    versionCode = 11
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://www.deviantart.com"
    }

    deeplink {
        host("www.deviantart.com")
        host("deviantart.com")
        path("/..*/gallery/..*")
    }
}

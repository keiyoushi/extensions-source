plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DeviantArt"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

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

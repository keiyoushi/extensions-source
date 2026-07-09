plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DeviantArt"
    versionCode = 37
    contentWarning = ContentWarning.NSFW
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

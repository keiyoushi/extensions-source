plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "3600000 Beauty"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://3600000.xyz"
    }

    deeplink {
        host("3600000.xyz")
        path("/..*")
    }
}

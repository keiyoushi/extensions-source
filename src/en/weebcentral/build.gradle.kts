plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Weeb Central"
    versionCode = 22
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://weebcentral.com"
    }

    deeplink {
        host("weebcentral.com")
        path("/series/..*")
    }
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cutie Comics"
    versionCode = 5
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://cutiecomics.com"
    }

    deeplink {
        host("cutiecomics.com")
        path("/..*")
    }
}

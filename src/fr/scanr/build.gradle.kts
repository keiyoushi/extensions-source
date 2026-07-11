plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ScanR"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://teamscanr.fr"
    }

    deeplink {
        host("teamscanr.fr")
        path("/..*")
    }
}

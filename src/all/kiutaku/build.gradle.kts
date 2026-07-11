plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kiutaku"
    versionCode = 6
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://kiutaku.com"
        id = 3040035304874076216L
    }

    deeplink {
        host("kiutaku.com")
        path("/..*")
    }
}

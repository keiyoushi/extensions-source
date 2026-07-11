plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comix"
    versionCode = 33
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://comix.to"
    }

    deeplink {
        host("comix.to")
        host("www.comix.to")
        path("/title/..*")
    }
}

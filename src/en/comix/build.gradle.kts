plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comix"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
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

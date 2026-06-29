plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mitaku"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://mitaku.net"
    }

    deeplink {
        host("mitaku.net")
        path("/..*")
        path("/.*/..*")
    }
}

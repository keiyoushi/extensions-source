plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Rawkuma"
    versionCode = 35
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"
    theme = "natsuid"

    source {
        lang = "ja"
        baseUrl = "https://rawkuma.net"
        versionId = 2
    }

    deeplink {
        host("rawkuma.net")
        path("/manga/..*")
    }
}

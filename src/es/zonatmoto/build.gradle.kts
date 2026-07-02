plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zonatmo.to (unoriginal)"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl = "https://zonatmo.to"
    }

    deeplink {
        host("zonatmo.to")
        host("www.zonatmo.to")
        path("/manga/..*")
    }
}

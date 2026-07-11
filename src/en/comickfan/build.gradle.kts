plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ComicK Fanmade"
    versionCode = 3
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://comickfan.com"
    }

    deeplink {
        host("comickfan.com")
        path("/manga/..*")
    }
}

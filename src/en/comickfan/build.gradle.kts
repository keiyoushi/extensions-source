plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ComicK Fanmade"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
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

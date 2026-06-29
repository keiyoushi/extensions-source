plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ComicK Fanmade"
    className = "ComicKFan"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    baseUrl = "https://comickfan.com"

    deeplink {
        host("comickfan.com")
        path("/manga/..*")
    }
}

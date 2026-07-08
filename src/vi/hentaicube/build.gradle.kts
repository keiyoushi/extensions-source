plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "CBHentai"
    versionCode = 33
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "vi"
        baseUrl {
            custom("https://2tencb.pro")
        }
        id = 823638192569572166L
    }

    deeplink {
        path("/read/..*")
    }
}

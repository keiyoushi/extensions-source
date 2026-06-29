plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyHentaiGallery"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://myhentaigallery.com"
    }

    deeplink {
        host("myhentaigallery.com")
        path("/gallery/thumbnails/..*")
    }
}

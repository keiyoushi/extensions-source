plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyHentaiGallery"
    className = "MyHentaiGallery"
    versionCode = 9
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("myhentaigallery.com")
        path("/gallery/thumbnails/..*")
    }
}

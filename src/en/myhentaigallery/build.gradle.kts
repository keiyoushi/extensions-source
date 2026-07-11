plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyHentaiGallery"
    versionCode = 10
    contentWarning = ContentWarning.LEGACY_NSFW_OR_MIXED
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

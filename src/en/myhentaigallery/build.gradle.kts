import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyHentaiGallery"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://myhentaigallery.com"
    }

    deeplink {
        host("myhentaigallery.com")
        path("/gallery/thumbnails/..*")
    }
}

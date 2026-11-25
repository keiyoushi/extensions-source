import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Kütüphanem"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://mangakutuphanem.com"
        lang = "tr"
    }

    deeplink {
        path("/seri-bilgi/..*")
    }
}

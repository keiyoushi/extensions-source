import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenHentaivn"
    versionCode = 6
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://truyenhentaivn.store")
        }
    }

    deeplink {
        path("/..*-doc-truyen-..*\\.html")
        path("/..*-..*-xem-truyen-..*\\.html")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TruyenHentaiz"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://truyenhentaiz.net")
        }
    }

    deeplink {
        path("/..*\\.html")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comix"
    versionCode = 38
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://comix.to",
                "https://comix.ws",
            )
        }
    }

    deeplink {
        host("comix.to")
        host("www.comix.to")
        host("comix.ws")
        host("www.comix.ws")
        path("/title/..*")
    }
}

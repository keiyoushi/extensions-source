import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NoyAcg"
    versionCode = 7
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh-Hant"
        baseUrl {
            mirrors(
                "https://api.noyteam.online",
                "https://api.noymanga.com",
                "https://api.noy.asia",
            )
        }
    }

    deeplink {
        host("beta.noyteam.online")
        host("noymanga.com")
        path("/manga/..*")
    }
}

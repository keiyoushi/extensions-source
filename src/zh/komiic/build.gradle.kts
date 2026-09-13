import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komiic"
    versionCode = 10
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh"

        baseUrl {
            mirrors(
                "https://komiic.com",
                "https://komiic.cc",
            )
        }
    }

    deeplink {
        host("komiic.com")
        host("komiic.cc")
        path("/comic/..*")
    }
}

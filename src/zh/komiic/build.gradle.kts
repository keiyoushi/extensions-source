plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Komiic"
    versionCode = 9
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "zh"

        baseUrl("https://komiic.com") {
            mirrors = listOf(
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

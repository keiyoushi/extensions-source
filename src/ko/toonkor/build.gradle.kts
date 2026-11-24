import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Toonkor"
    versionCode = 14
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    deeplink {
        path("/..*")
    }

    source {
        lang = "ko"
        baseUrl {
            mirrors(
                "툰코" to "https://009.toonkor.net",
                "펀비" to "https://009.funbe.org",
            )
        }
    }
}

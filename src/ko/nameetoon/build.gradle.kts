import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NameeToon"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "나미툰"
        lang = "ko"
        baseUrl {
            mirrors(
                "나미툰" to "https://namee134.asia",
                "머니툰" to "https://mtoon174.asia",
                "무료툰" to "https://foc145.asia",
            )
        }
    }

    deeplink {
        path("/list.php")
    }
}

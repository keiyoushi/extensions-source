import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakakalot"
    versionCode = 10
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "" to "https://www.katamanga.com",
                "www.mangakakalove.com (legacy)" to "https://www.mangakakalove.com",
            )
        }
    }
}

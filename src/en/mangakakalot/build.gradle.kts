import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakakalot"
    versionCode = 9
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://www.mangakakalot.gg",
                "https://www.mangakakalove.com",
            )
        }
    }
}

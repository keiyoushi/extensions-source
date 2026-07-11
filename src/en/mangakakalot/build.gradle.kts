import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangakakalot"
    versionCode = 8
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
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

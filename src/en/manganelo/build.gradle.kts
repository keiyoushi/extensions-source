import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manganato"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "https://www.natomanga.com",
                "https://www.nelomanga.com",
                "https://www.nelomanga.net",
                "https://www.manganato.gg",
            )
        }
        id = 1024627298672457456L
    }
}

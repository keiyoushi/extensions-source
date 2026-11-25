import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manganato"
    versionCode = 7
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangabox"

    source {
        lang = "en"
        baseUrl {
            mirrors(
                "" to "https://www.natomanga.com",
                "www.nelomanga.net (legacy)" to "https://www.nelomanga.net",
                "www.manganato.gg (legacy)" to "https://www.manganato.gg",
            )
        }
        id = 1024627298672457456L
    }
}

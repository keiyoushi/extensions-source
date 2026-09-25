import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa-Latino"
    versionCode = 11
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "es"
        baseUrl {
            mirrors(
                "https://manhwa-latino.com",
                "https://manhwa-es.com",
            )
        }
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Klmanga"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zing"

    source {
        lang = "ja"
        baseUrl {
            custom("https://klmanga.show")
        }
    }

    deeplink {
        path("/manga-raw/..*")
        path("/manga-genre/..*")
    }
}

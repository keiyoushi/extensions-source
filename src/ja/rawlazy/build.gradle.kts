import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RawLazy"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zing"

    source {
        lang = "ja"
        baseUrl {
            custom("https://rawlazy.io")
        }
    }

    // Chapter pages sit at the site root, next to /manga-lazy/ and /genres/
    deeplink {
        path("/..*")
    }
}

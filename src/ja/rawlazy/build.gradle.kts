import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "RawLazy"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "zingtheme"

    source {
        lang = "ja"
        baseUrl {
            custom("https://rawlazy.io")
        }
    }

    deeplink {
        path("/manga-lazy/..*")
    }
}

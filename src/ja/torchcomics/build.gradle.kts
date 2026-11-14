import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Torch Comics"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://to-ti.in"
        lang = "ja"
    }

    deeplink {
        path("/product/..*")
        path("/story/..*")
    }
}

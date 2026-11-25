import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReadToon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "th"
        baseUrl = "https://readtoon.com"
    }

    deeplink {
        host("readtoon.com")
        host("www.readtoon.com")
        path("/content/..*")
    }
}

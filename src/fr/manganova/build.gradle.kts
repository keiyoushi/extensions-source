import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaNova"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://www.manga-nova.com"
    }

    deeplink {
        host("www.manga-nova.com")
        path("/lecture-en-ligne/..*")
        path("/manga/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AfterToon"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://aftertoon.com"
    }

    deeplink {
        host("aftertoon.com")
        host("www.aftertoon.com")
        path("/manga")
        path("/manga/..*")
        path("/tur/..*")
    }
}

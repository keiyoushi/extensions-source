import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ninko Comics"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://ninkocomics.com"
        lang = "en"
    }

    deeplink {
        host("ninkocomics.com")
        host("www.ninkocomics.com")
        path("/comic/..*")
        path("/chapter/..*")
    }
}

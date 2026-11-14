import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Punk Records"
    pkgName = "all.littlegarden"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://punkrecordz.com"
        id = 4646674279369583651L
    }

    deeplink {
        host("punkrecordz.com")
        path("/manga/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ONF MANGAS"
    versionCode = 7
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://onfmangas.com"
    }

    deeplink {
        host("onfmangas.com")
        host("www.onfmangas.com")
        path("/manga/..*")
    }
}

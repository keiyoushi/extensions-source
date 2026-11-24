import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaAR"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "مانغا عربي"
        lang = "ar"
        baseUrl = "https://mangaar.com"
    }

    deeplink {
        host("mangaar.com")
        host("www.mangaar.com")
        path("/manga/..*")
        path("/category/..*")
    }
}

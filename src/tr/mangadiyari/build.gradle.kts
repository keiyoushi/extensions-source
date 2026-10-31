import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Diyarı"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://mangadiyari.com"
        lang = "tr"
    }

    deeplink {
        path("/series/..*")
        path("/seri/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Portalı"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://www.mangaportali.com"
        lang = "tr"
    }

    deeplink {
        path("/series/..*")
    }
}

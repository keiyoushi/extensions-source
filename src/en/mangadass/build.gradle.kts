import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manga Dass"
    versionCode = 58
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "yiimadara"

    source {
        lang = "en"
        baseUrl = "https://mangadass.com"
    }

    deeplink {
        path("/manga/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Seinagi"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "foolslide"

    source {
        lang = "en"
        baseUrl = "https://reader.seinagi.org.es"
    }

    source {
        lang = "es"
        baseUrl = "https://online.seinagi.org.es"
    }

    source {
        name = "Seinagi Adulto"
        lang = "es"
        baseUrl = "https://adulto.seinagi.org.es"
    }

    deeplink {
        host("reader.seinagi.org.es")
        host("online.seinagi.org.es")
        host("adulto.seinagi.org.es")
        path("/series/..*")
        path("/read/..*")
    }
}

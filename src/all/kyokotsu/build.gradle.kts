import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kyokotsu"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "ru"
        baseUrl = "https://kyokotsu.com"
    }
    source {
        lang = "en"
        baseUrl = "https://kyokotsu.com"
    }

    deeplink {
        path("/?e?n?/manga/..*")
        path("/?e?n?/manhwa/..*")
        path("/?e?n?/manhua/..*")
        path("/?e?n?/comics/..*")
        path("/?e?n?/oel-manga/..*")
        path("/?e?n?/rumanga/..*")
        path("/?e?n?/runet-comics/..*")
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}

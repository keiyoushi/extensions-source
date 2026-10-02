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
        path("/manga/..*")
        path("/manhwa/..*")
        path("/manhua/..*")
        path("/comics/..*")
        path("/oel-manga/..*")
        path("/rumanga/..*")
        path("/runet-comics/..*")
    }
}

dependencies {
    implementation(project(":lib:i18n"))
}

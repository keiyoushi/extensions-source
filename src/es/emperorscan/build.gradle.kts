import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Emperor Scan"
    versionCode = 70
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "es"
        baseUrl = "https://imperiomanhwa.com"
    }

    deeplink {
        path("/manga/..*")
    }
}

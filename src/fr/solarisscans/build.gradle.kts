import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Solaris Scans"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://solaris-scans.fr"
        lang = "fr"
    }

    deeplink {
        path("/manga/..*")
    }
}

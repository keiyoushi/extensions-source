import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nemesis scans"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "Nemesisscans"
        lang = "tr"
        baseUrl = "https://nemesisscans.com"
    }

    deeplink {
        path("/series/..*")
    }
}

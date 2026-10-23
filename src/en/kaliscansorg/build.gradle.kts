import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KaliScans.org (unoriginal)"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://kaliscans.org"
    }

    deeplink {
        path("/series/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Internet Archive"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://archive.org"
    }

    deeplink {
        path("/details/..*")
        path("/download/..*")
        path("/stream/..*")
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kodoku Studio"
    versionCode = 56
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://kodokueasyaccess.com"
        versionId = 2
    }

    deeplink {
        path("/manhwa/..*")
        path("/read/..*")
    }
}

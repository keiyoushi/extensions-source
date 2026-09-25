import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Banchan Scan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://banchanscan.fr"
        lang = "fr"
    }

    deeplink {
        path("/webtoon/..*")
    }
}

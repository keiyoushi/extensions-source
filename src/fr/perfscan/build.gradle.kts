import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Perf Scan"
    versionCode = 0
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.6"
    theme = "loneseal"

    source {
        lang = "fr"
        baseUrl = "https://perf-scan.xyz"
        versionId = 2
    }

    deeplink {
        path("/series/..*")
    }
}

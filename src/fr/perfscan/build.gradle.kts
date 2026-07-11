import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Perf Scan"
    versionCode = 31
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "fr"
        baseUrl = "https://perf-scan.xyz"
        versionId = 2
    }
}

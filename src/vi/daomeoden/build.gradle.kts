import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DaoMeoDen"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl = "https://daomeoden.net"
    }
}

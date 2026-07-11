import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NhatTruyen"
    versionCode = 21
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl = "https://nhattruyenqq.com"
    }
}

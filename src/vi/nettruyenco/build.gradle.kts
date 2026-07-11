import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NetTruyenCO (unoriginal)"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl = "https://nettruyenar.com"
    }
}

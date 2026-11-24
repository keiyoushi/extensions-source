import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NetTruyenS (unoriginal)"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl {
            custom("https://nettruyen16s.com")
        }
    }
}

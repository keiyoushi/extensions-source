import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Top Truyen"
    versionCode = 32
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.toptruyenzone7.com")
        }
    }
}

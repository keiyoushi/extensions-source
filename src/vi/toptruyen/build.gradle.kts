import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Top Truyen"
    versionCode = 39
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.toptruyenzone12.com")
        }
    }
}

import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Top Truyen"
    versionCode = 37
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.toptruyenzonee.com")
        }
    }
}

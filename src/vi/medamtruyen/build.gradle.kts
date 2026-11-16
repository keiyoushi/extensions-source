import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeDamTruyen"
    versionCode = 14
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "koi"

    source {
        lang = "vi"
        baseUrl {
            custom("https://saytongtaii.site")
        }
    }
}

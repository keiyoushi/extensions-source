import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MeDamTruyen"
    versionCode = 8
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://saytongtaii.site")
        }
    }

    deeplink {
        path("/.*")
    }
}

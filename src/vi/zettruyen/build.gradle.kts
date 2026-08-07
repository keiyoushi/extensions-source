import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZetTruyen"
    versionCode = 12
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.zettruyen.homes")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}

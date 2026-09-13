import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ZetTruyen"
    versionCode = 13
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://www.zettruyen1.com")
        }
    }

    deeplink {
        path("/truyen-tranh/..*")
    }
}

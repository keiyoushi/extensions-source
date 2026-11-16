import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kaguya"
    pkgName = "id.yubikiri"
    versionCode = 65
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "id"
        baseUrl = "https://01.kaguya.re"
        id = 1557304490417397104L
    }

    deeplink {
        path("/comic/..*")
    }
}

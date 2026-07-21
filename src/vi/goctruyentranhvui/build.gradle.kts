import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goc Truyen Tranh Vui"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl {
            custom("https://goctruyentranhvui41.com")
        }
    }

    deeplink {
        host("goctruyentranhvui41.com")
        path("/truyen/..*")
    }
}

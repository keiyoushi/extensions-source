import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goc Truyen Tranh Vui"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl {
            custom("https://goctruyentranhvui30.com")
        }
    }

    deeplink {
        host("goctruyentranhvui30.com")
        path("/truyen/..*")
    }
}

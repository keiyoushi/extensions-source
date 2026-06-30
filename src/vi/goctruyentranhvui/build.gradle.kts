plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goc Truyen Tranh Vui"
    className = "GocTruyenTranhVui"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://goctruyentranhvui30.com") {
            withCustom.set(true)
        }
    }

    deeplink {
        host("goctruyentranhvui30.com")
        path("/truyen/..*")
    }
}

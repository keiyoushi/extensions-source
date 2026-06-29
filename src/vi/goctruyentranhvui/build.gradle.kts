plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Goc Truyen Tranh Vui"
    className = "GocTruyenTranhVui"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("goctruyentranhvui30.com")
        path("/truyen/..*")
    }
}

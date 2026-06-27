plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MANGA Plus by SHUEISHA"
    className = "MangaPlusFactory"
    versionCode = 62
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("mangaplus.shueisha.co.jp")
        host("www.mangaplus.shueisha.co.jp")
        host("jumpg-webapi.tokyo-cdn.com")
        host("www.jumpg-webapi.tokyo-cdn.com")
        path("/titles/..*")
        path("/viewer/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}

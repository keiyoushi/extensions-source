plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Baozi Manhua"
    className = "Baozi"
    versionCode = 28
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("baozimh.com")
        host("*.baozimh.com")
        host("webmota.com")
        host("*.webmota.com")
        host("kukuc.co")
        host("*.kukuc.co")
        host("twmanga.com")
        host("*.twmanga.com")
        host("dinnerku.com")
        host("*.dinnerku.com")
        path("/comic/..*")
        path("/comic/chapter/..*")
    }
}

dependencies {

    implementation("com.github.stevenyomi:baozibanner:9ac9b08e1d") // 1.0
}

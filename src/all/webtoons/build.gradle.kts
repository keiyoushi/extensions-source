plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webtoons.com"
    className = "WebtoonsFactory"
    versionCode = 55
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("webtoons.com")
        host("www.webtoons.com")
        host("m.webtoons.com")
        path("/.*/.*/.*/..*")
        path("/.*/.*/.*/.*/..*")
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
    implementation(project(":lib:textinterceptor"))
}

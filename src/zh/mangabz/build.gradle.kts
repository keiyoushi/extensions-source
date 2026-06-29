plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangabz"
    className = "Mangabz"
    versionCode = 14
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("mangabz.com")
        host("xmanhua.com")
        host("yymanhua.com")
        path("/..*")
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
    implementation(project(":lib:unpacker"))
}

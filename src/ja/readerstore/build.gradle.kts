plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Reader Store"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://ebookstore.sony.jp"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}

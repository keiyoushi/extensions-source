plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FOD"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://manga.fod.fujitv.co.jp"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "eBookJapan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://ebookjapan.yahoo.co.jp"
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MoeTruyen"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "vi"
        baseUrl("https://moetruyen.net") {
            mirrors = listOf("https://truyen.moe")
        }
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}

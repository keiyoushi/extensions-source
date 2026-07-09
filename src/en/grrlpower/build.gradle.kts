plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Grrl Power Comic"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.grrlpowercomic.com"
    }
}

dependencies {
    implementation(project(":lib:textinterceptor"))
}

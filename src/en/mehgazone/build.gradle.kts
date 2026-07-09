plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mehgazone"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://mehgazone.com"
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}

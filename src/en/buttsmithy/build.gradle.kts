plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "buttsmithy"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        name = "Buttsmithy"
        lang = "en"
        baseUrl = "https://incase.buttsmithy.com"
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}

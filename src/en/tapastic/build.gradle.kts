plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tapas"
    versionCode = 24
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://tapas.io"
        id = 5554748812413853237L
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webcomics"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://webcomicsapp.com"
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}

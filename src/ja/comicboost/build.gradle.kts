plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comic Boost"
    versionCode = 2
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://comic-boost.com"
    }
}

dependencies {

    implementation(project(":lib:publus"))
}

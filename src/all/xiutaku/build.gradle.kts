plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Xiutaku"
    className = "Xiutaku"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("xiutaku.com")
        path("/.*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}

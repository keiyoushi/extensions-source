plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Atemporal"
    className = "Atemporal"
    versionCode = 15
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "mangathemesia"
    baseUrl = "https://atemporal.cloud"
}

dependencies {

    api(project(":lib:cookieinterceptor"))
}

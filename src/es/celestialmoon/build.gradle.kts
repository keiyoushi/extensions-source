plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Celestial Moon"
    className = "CelestialMoon"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "mangathemesia"
    baseUrl = "https://celestialmoonscan.es"
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
}

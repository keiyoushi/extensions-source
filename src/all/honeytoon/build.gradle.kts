plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Honeytoon"
    className = "HoneytoonFactory"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    deeplink {
        host("honeytoon.com")
        path("/comic/..*")
        path("/de/comic/..*")
        path("/es/comic/..*")
        path("/fr/comic/..*")
        path("/it/comic/..*")
        path("/pt/comic/..*")
    }
}

dependencies {

    implementation(project(":lib:cookieinterceptor"))
    implementation(project(":lib:i18n"))
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ReiManga"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://reimanga.com"
    }

    deeplink {
        path("/manga/..*")
    }
}

dependencies {
    implementation(project(":lib:cookieinterceptor"))
}

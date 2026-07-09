plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Project Suki"
    versionCode = 9
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://projectsuki.com"
        id = 8965918600406781666L
    }

    deeplink {
        host("projectsuki.com")
        path("/search.*")
        path("/book/.*")
        path("/read/.*")
    }
}

dependencies {
    implementation(project(":lib:randomua"))
}

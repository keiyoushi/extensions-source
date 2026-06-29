plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Project Suki"
    className = "ProjectSuki"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    deeplink {
        host("projectsuki.com")
        path("/search.*")
    }

    deeplink {
        host("projectsuki.com")
        path("/book/.*")
    }

    deeplink {
        host("projectsuki.com")
        path("/read/.*")
    }
}

dependencies {

    implementation(project(":lib:randomua"))
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Project Suki"
    className = "ProjectSuki"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:randomua"))
}

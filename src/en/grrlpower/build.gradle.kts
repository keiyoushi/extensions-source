plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Grrl Power Comic"
    className = "GrrlPower"
    versionCode = 4
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Questionable Content"
    className = "QuestionableContent"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}

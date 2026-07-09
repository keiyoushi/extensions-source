plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Questionable Content"
    versionCode = 10
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://www.questionablecontent.net"
    }
}

dependencies {

    implementation(project(":lib:textinterceptor"))
}

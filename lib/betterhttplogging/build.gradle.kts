plugins {
    id("lib-android")
}

dependencies {
    implementation(libs.okhttp.logging)
    implementation(project(":lib:logcatchunker"))
}

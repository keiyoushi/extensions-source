plugins {
    id("lib-android")
}

dependencies {
    compileOnly(libs.okhttp.logging)
    implementation(project(":lib:logcatchunker"))
}

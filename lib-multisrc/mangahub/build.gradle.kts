plugins {
    id("lib-multisrc")
}

baseVersionCode = 33

dependencies {
    api(project(":lib:randomua"))
    implementation(libs.dec)
}

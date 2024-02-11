plugins {
    id("lib-multisrc")
}

extra["baseVersionCode"] = 1

dependencies {
    implementation(project(":lib:synchrony"))
}

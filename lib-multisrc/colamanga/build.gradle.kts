plugins {
    id("lib-multisrc")
}

baseVersionCode = 10

dependencies {
    api(project(":lib:synchrony"))
    implementation(project(":lib:dataimage"))
    implementation(project(":lib:randomua"))
}

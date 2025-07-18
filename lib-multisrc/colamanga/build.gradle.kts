plugins {
    id("lib-multisrc")
}

baseVersionCode = 10

dependencies {
    api(project(":lib:synchrony"))
    implementation(project(":lib:dataimage"))
    implementation(project(":lib:randomua"))
    implementation(project(":lib:i18n"))
}

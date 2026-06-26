import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.net.URL

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Wolf.com"
    className = "WolfFactory"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val updateTask = tasks.register<UpdateDomainNumberTask>("update${variantName}DomainNumber") {
            defaultDomainNumber.set(393)
            outputs.upToDateWhen { false }
        }
        @Suppress("UnstableApiUsage")
        variant.sources.kotlin?.addGeneratedSourceDirectory(updateTask) { it.outputDir }
    }
}

abstract class UpdateDomainNumberTask : DefaultTask() {

    @get:Input
    abstract val defaultDomainNumber: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun action() {
        var domainNumber = -1
        try {
            val response = URL("https://nicelink52.com/").readText()
            val matcher = Regex("""https?://wfwf(\d+)\.com""").find(response)
            if (matcher != null) {
                domainNumber = matcher.groupValues[1].toInt()
                println("[Wolf.com] new domain number: $domainNumber")
            } else {
                domainNumber = defaultDomainNumber.get()
                println("[Wolf.com] domain number not found using default: $domainNumber")
            }
        } catch (e: Exception) {
            domainNumber = defaultDomainNumber.get()
            println("[Wolf.com] error fetching domain number, using default: $domainNumber")
        }

        val domainNumberFile = outputDir.get().file("DomainNumber.kt").asFile
        domainNumberFile.parentFile.mkdirs()
        domainNumberFile.printWriter().use { out ->
            out.println("package eu.kanade.tachiyomi.extension.ko.wolfdotcom\n")
            out.println("const val DEFAULT_DOMAIN_NUMBER = \"$domainNumber\"")
        }
    }
}

package generator

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * This is meant to be used in place of a factory extension, specifically for what would be a multi-source extension.
 * A multi-lang (but not multi-source) extension should still be made as a factory extension.
 * Use a generator for initial setup of a theme source or when all of the inheritors need a version bump.
 * Source list (val sources) should be kept up to date.
 */
interface ThemeSourceGenerator {
    /**
     * The class that the sources inherit from.
     */
    val themeClass: String

    /**
     * The package that contains themeClass.
     */
    val themePkg: String

    /**
     * Base theme version, starts with 1 and should be increased when based theme class changes
     */
    val baseVersionCode: Int

    /**
     * The list of sources to be created or updated.
     */
    val sources: List<ThemeSourceData>

    fun createAll() {
        val userDir = System.getProperty("user.dir")!!
        sources.forEach { createGradleProject(it, themePkg, themeClass, baseVersionCode, userDir) }
    }

    companion object {
        private fun pkgNameSuffix(source: ThemeSourceData, separator: String): String {
            return if (source is ThemeSourceData.SingleLang) {
                listOf(source.lang.substringBefore("-"), source.pkgName).joinToString(separator)
            } else {
                listOf("all", source.pkgName).joinToString(separator)
            }
        }

        private fun themeSuffix(themePkg: String, separator: String): String {
            return listOf("eu", "kanade", "tachiyomi", "multisrc", themePkg).joinToString(separator)
        }

        private fun writeGradle(gradle: File, source: ThemeSourceData, themePkg: String, baseVersionCode: Int, defaultAdditionalGradlePath: String, additionalGradleOverridePath: String) {
            fun File.readTextOrEmptyString(): String = if (exists()) readText(Charsets.UTF_8) else ""

            val defaultAdditionalGradleText = File(defaultAdditionalGradlePath).readTextOrEmptyString()
            val additionalGradleOverrideText = File(additionalGradleOverridePath).readTextOrEmptyString()
            val placeholders = mapOf(
                "SOURCEHOST" to source.baseUrl.toHttpUrlOrNull()?.host,
                "SOURCESCHEME" to source.baseUrl.toHttpUrlOrNull()?.scheme,
            )

            val placeholdersStr = placeholders
                .filter { it.value != null }
                .map { "${" ".repeat(12)}${it.key}: \"${it.value}\"" }
                .joinToString(",\n")

            gradle.writeText(
                """
                |// THIS FILE IS AUTO-GENERATED; DO NOT EDIT
                |ext {
                |    extName = '${source.name}'
                |    extClass = '.${source.className}'
                |    extVersionCode = ${baseVersionCode + source.overrideVersionCode + multisrcLibraryVersion}
                |    ${if (source.isNsfw) "isNsfw = true\n" else ""}
                |}
                |
                |apply from: "${'$'}rootDir/common.gradle"
                |
                |$defaultAdditionalGradleText
                |$additionalGradleOverrideText
                |
                |android {
                |    defaultConfig {
                |        manifestPlaceholders += [
                |$placeholdersStr
                |        ]
                |    }
                |}
                |
                """.trimMargin(),
            )
        }

        private fun writeAndroidManifest(androidManifestFile: File, manifestOverridesPath: String, defaultAndroidManifestPath: String) {
            val androidManifestOverride = File(manifestOverridesPath)
            val defaultAndroidManifest = File(defaultAndroidManifestPath)
            if (androidManifestOverride.exists()) {
                androidManifestOverride.copyTo(androidManifestFile)
            } else if (defaultAndroidManifest.exists()) {
                defaultAndroidManifest.copyTo(androidManifestFile)
            }
        }

        fun createGradleProject(source: ThemeSourceData, themePkg: String, themeClass: String, baseVersionCode: Int, userDir: String) {
            // userDir = tachiyomi-extensions project root path
            val projectRootPath = "$userDir/generated-src/${pkgNameSuffix(source, "/")}"
            val projectSrcPath = "$projectRootPath/src/eu/kanade/tachiyomi/extension/${pkgNameSuffix(source, "/")}"
            val overridesPath = "$userDir/multisrc/overrides/$themePkg/${source.pkgName}"
            val defaultResPath = "$userDir/multisrc/overrides/$themePkg/default/res"
            val defaultAndroidManifestPath = "$userDir/multisrc/overrides/$themePkg/default/AndroidManifest.xml"
            val defaultAdditionalGradlePath = "$userDir/multisrc/overrides/$themePkg/default/additional.gradle"
            val resOverridePath = "$overridesPath/res"
            val srcOverridePath = "$overridesPath/src"
            val manifestOverridePath = "$overridesPath/AndroidManifest.xml"
            val additionalGradleOverridePath = "$overridesPath/additional.gradle"
            val projectGradleFile = File("$projectRootPath/build.gradle")
            val projectAndroidManifestFile = File("$projectRootPath/AndroidManifest.xml")

            File(projectRootPath).let { projectRootFile ->
                println("Generating $source")

                // remove everything from past runs
                projectRootFile.deleteRecursively()
                projectRootFile.mkdirs()

                writeGradle(projectGradleFile, source, themePkg, baseVersionCode, defaultAdditionalGradlePath, additionalGradleOverridePath)
                writeAndroidManifest(projectAndroidManifestFile, manifestOverridePath, defaultAndroidManifestPath)

                writeSourceClasses(projectSrcPath, srcOverridePath, source, themePkg, themeClass)
                copyThemeClasses(userDir, themePkg, projectRootPath)
                copyThemeReadmes(userDir, themePkg, overridesPath, projectRootPath)
                copyResFiles(resOverridePath, defaultResPath, source, projectRootPath)
            }
        }

        private fun copyThemeReadmes(userDir: String, themePkg: String, overridesPath: String, projectRootPath: String) {
            val sourcePath = "$userDir/multisrc/src/main/java/${themeSuffix(themePkg, "/")}"

            File(projectRootPath).mkdirs()

            listOf(sourcePath, overridesPath).forEach { path ->
                File(path).list()
                    ?.filter { it.endsWith("README.md") || it.endsWith("CHANGELOG.md") }
                    ?.forEach {
                        Files.copy(
                            File("$path/$it").toPath(),
                            File("$projectRootPath/$it").toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
            }
        }

        private fun copyThemeClasses(userDir: String, themePkg: String, projectRootPath: String) {
            val themeSrcPath = "$userDir/multisrc/src/main/java/${themeSuffix(themePkg, "/")}"

            val themeDestPath = "$projectRootPath/src/${themeSuffix(themePkg, "/")}"
            File(themeDestPath).mkdirs()

            File(themeSrcPath).list()
                ?.filter { it.endsWith(".kt") && !it.endsWith("Generator.kt") && !it.endsWith("Gen.kt") }
                ?.forEach { Files.copy(File("$themeSrcPath/$it").toPath(), File("$themeDestPath/$it").toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }

        private fun copyResFiles(resOverridePath: String, defaultResPath: String, source: ThemeSourceData, projectRootPath: String): Any {
            // check if res override exists if not copy default res
            val resOverride = File(resOverridePath)
            return if (resOverride.exists()) {
                resOverride.copyRecursively(File("$projectRootPath/res"))
            } else {
                File(defaultResPath).let { defaultResFile ->
                    if (defaultResFile.exists()) defaultResFile.copyRecursively(File("$projectRootPath/res"))
                }
            }
        }

        private fun writeSourceClasses(projectSrcPath: String, srcOverridePath: String, source: ThemeSourceData, themePkg: String, themeClass: String) {
            val projectSrcFile = File(projectSrcPath)
            projectSrcFile.mkdirs()

            val srcOverrideFile = File(srcOverridePath)
            if (srcOverrideFile.exists()) {
                srcOverrideFile.copyRecursively(projectSrcFile)
            } else {
                writeSourceClass(projectSrcFile, source, themePkg, themeClass)
            }
        }

        private fun writeSourceClass(classPath: File, source: ThemeSourceData, themePkg: String, themeClass: String) {
            fun factoryClassText() = when (source) {
                is ThemeSourceData.SingleLang -> {
                    """class ${source.className} : $themeClass("${source.sourceName}", "${source.baseUrl}", "${source.lang}")"""
                }
                is ThemeSourceData.MultiLang -> {
                    val sourceClasses = source.langs.map { lang ->
                        """$themeClass("${source.sourceName}", "${source.baseUrl}", "$lang")"""
                    }

                    """
                    |class ${source.className} : SourceFactory {
                    |    override fun createSources() = listOf(
                    |        ${sourceClasses.joinToString(",\n")}
                    |    )
                    |}
                    """.trimMargin()
                }
            }

            File("$classPath/${source.className}.kt").writeText(
                """
                |/* ktlint-disable */
                |// THIS FILE IS AUTO-GENERATED; DO NOT EDIT
                |package eu.kanade.tachiyomi.extension.${pkgNameSuffix(source, ".")}
                |
                |import eu.kanade.tachiyomi.multisrc.$themePkg.$themeClass
                |${if (source is ThemeSourceData.MultiLang) "import eu.kanade.tachiyomi.source.SourceFactory" else ""}
                |
                |${factoryClassText()}
                """.trimMargin(),
            )
        }
    }
}

sealed class ThemeSourceData {
    abstract val name: String
    abstract val baseUrl: String
    abstract val isNsfw: Boolean
    abstract val className: String
    abstract val pkgName: String

    /**
     * Override it if for some reason the name attribute inside the source class
     * should be different from the extension name. Useful in cases where the
     * extension name should be romanized and the source name should be the one
     * in the source language. Defaults to the extension name if not specified.
     */
    abstract val sourceName: String

    /**
     * overrideVersionCode defaults to 0, if a source changes their source override code or
     * a previous existing source suddenly needs source code overrides, overrideVersionCode
     * should be increased.
     * When a new source is added with overrides, overrideVersionCode should still be set to 0
     *
     * Note: source code overrides are located in "multisrc/overrides/src/<themeName>/<sourceName>"
     */
    abstract val overrideVersionCode: Int

    data class SingleLang(
        override val name: String,
        override val baseUrl: String,
        val lang: String,
        override val isNsfw: Boolean = false,
        override val className: String = name.replace(" ", ""),
        override val pkgName: String = className.lowercase(Locale.ENGLISH),
        override val sourceName: String = name,
        override val overrideVersionCode: Int = 0,
    ) : ThemeSourceData()

    data class MultiLang(
        override val name: String,
        override val baseUrl: String,
        val langs: List<String>,
        override val isNsfw: Boolean = false,
        override val className: String = name.replace(" ", "") + "Factory",
        override val pkgName: String = className.substringBefore("Factory").lowercase(Locale.ENGLISH),
        override val sourceName: String = name,
        override val overrideVersionCode: Int = 0,
    ) : ThemeSourceData()
}

/**
 * This variable should be increased when the multisrc library changes in a way that prompts global extension upgrade
 */
const val multisrcLibraryVersion = 0

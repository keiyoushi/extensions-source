package keiyoushi.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PartialLocaleStrings(
    val mirrorTitle: String? = null,
    val customUrlTitle: String? = null,
    val customUrlDialogMessage: String? = null,
)

data class LocaleStrings(
    val mirrorTitle: String,
    val customUrlTitle: String,
    val customUrlDialogMessage: String,
)

@Serializable
data class BaseUrlSpecData(
    val type: String,
    val defaultUrl: String,
    val mirrors: List<MirrorData> = emptyList(),
)

@Serializable
data class MirrorData(
    val url: String,
    val label: String = "",
)

@Serializable
data class SourceDef(
    val name: String,
    val lang: String,
    val id: Long,
    val baseUrl: BaseUrlSpecData,
)

private const val HTTP_SOURCE = "eu.kanade.tachiyomi.source.online.HttpSource"

private fun KSClassDeclaration.derivesFromHttpSource(): Boolean =
    getAllSuperTypes().any { it.declaration.qualifiedName?.asString() == HTTP_SOURCE }

private val configurable = ClassName("eu.kanade.tachiyomi.source", "ConfigurableSource")
private val preferenceScreen = ClassName("androidx.preference", "PreferenceScreen")
private val mirrorPrefsClass = ClassName("keiyoushi.source", "MirrorPreferences")
private val customUrlPrefsClass = ClassName("keiyoushi.source", "CustomUrlPreferences")
private val getPreferencesFn = MemberName("keiyoushi.utils", "getPreferences")

class SourceProcessor(
    private val codeGenerator: CodeGenerator,
    private val options: Map<String, String>,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private val translations: Map<String, PartialLocaleStrings> by lazy {
        val path = options["kei_translations"] ?: return@lazy emptyMap()
        runCatching {
            Json.decodeFromString<Map<String, PartialLocaleStrings>>(File(path).readText())
        }.getOrElse {
            logger.warn("kei_translations: ${it.message}")
            emptyMap()
        }
    }

    private fun stringsForLang(lang: String): LocaleStrings {
        val en = translations.getValue("en")
        val locale = translations[lang] ?: translations[lang.substringBefore("-")]
        return LocaleStrings(
            mirrorTitle = locale?.mirrorTitle ?: en.mirrorTitle!!,
            customUrlTitle = locale?.customUrlTitle ?: en.customUrlTitle!!,
            customUrlDialogMessage = locale?.customUrlDialogMessage ?: en.customUrlDialogMessage!!,
        )
    }

    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val annotatedClasses = resolver
            .getSymbolsWithAnnotation("keiyoushi.annotation.Source")
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val sourcesJson = options["kei_sources"]

        if (annotatedClasses.isEmpty() && sourcesJson == null) return emptyList()

        if (annotatedClasses.isEmpty()) {
            logger.error("source {} blocks present but no @Source class found — annotate your source class with @Source")
            return emptyList()
        }

        if (annotatedClasses.size > 1) {
            val names = annotatedClasses.joinToString { it.qualifiedName?.asString() ?: it.simpleName.asString() }
            logger.error("exactly one @Source class allowed per module, found: $names", annotatedClasses.first())
            return emptyList()
        }

        val annotated = annotatedClasses.single()

        if (sourcesJson == null) {
            logger.error("@Source found but no source {} blocks in build.gradle.kts", annotated)
            return emptyList()
        }

        val sources = Json.decodeFromString<List<SourceDef>>(sourcesJson)
        if (sources.isEmpty()) {
            logger.error("@Source found but source list is empty", annotated)
            return emptyList()
        }

        val pkg = annotated.packageName.asString()
        val annotatedClass = annotated.toClassName()
        val superTypeNames = annotated.getAllSuperTypes()
            .mapNotNull { it.declaration.qualifiedName?.asString() }
            .toSet()
        val isConfigurable = "eu.kanade.tachiyomi.source.ConfigurableSource" in superTypeNames

        if (HTTP_SOURCE !in superTypeNames) {
            logger.error("@Source class must derive from HttpSource", annotated)
            return emptyList()
        }

        // any overrides below HttpSource
        val overridden = annotated.getAllProperties()
            .filter { Modifier.OVERRIDE in it.modifiers && Modifier.ABSTRACT !in it.modifiers }
            .filter { prop ->
                val owner = prop.parentDeclaration as? KSClassDeclaration
                owner != null && owner.qualifiedName?.asString() != HTTP_SOURCE && owner.derivesFromHttpSource()
            }
            .map { it.simpleName.asString() }
            .toSet()

        val fileProps = mutableListOf<PropertySpec>()

        val isConcrete = Modifier.ABSTRACT !in annotated.modifiers
        val generatedClass = if (sources.size == 1 && !isConcrete) {
            buildSingleSourceClass(annotatedClass, sources.single(), isConfigurable, overridden, annotated, fileProps)
        } else {
            buildSourceFactoryClass(annotatedClass, sources, isConfigurable, overridden, annotated, fileProps)
        }

        FileSpec.builder(pkg, "ExtensionGenerated")
            .apply { fileProps.forEach(::addProperty) }
            .addType(generatedClass)
            .build()
            .writeTo(codeGenerator, Dependencies(false, annotated.containingFile!!))

        return emptyList()
    }

    private fun buildSingleSourceClass(
        annotatedClass: ClassName,
        source: SourceDef,
        isConfigurable: Boolean,
        overridden: Set<String>,
        node: KSClassDeclaration,
        fileProps: MutableList<PropertySpec>,
    ): TypeSpec = TypeSpec.classBuilder("ExtensionGenerated")
        .addModifiers(KModifier.INTERNAL)
        .superclass(annotatedClass)
        .applySourceMembers(source, "", fileProps, isConfigurable, overridden, node)
        .build()

    private fun buildSourceFactoryClass(
        annotatedClass: ClassName,
        sources: List<SourceDef>,
        isConfigurable: Boolean,
        overridden: Set<String>,
        node: KSClassDeclaration,
        fileProps: MutableList<PropertySpec>,
    ): TypeSpec {
        val sourceFactoryType = ClassName("eu.kanade.tachiyomi.source", "SourceFactory")
        val sourceType = ClassName("eu.kanade.tachiyomi.source", "Source")

        // A concrete @Source class is instantiated directly (preserving its FQN) instead of
        // being wrapped in an anonymous subclass, whose qualifiedName would be null and break
        // app-side deligated sources or enhanced trackers
        val isConcrete = Modifier.ABSTRACT !in node.modifiers
        val ctorParams = node.primaryConstructor?.parameters.orEmpty()
            .mapNotNull { it.name?.asString() }
            .toSet()

        if (isConcrete) {
            validateConcreteSource(node, sources, overridden, ctorParams)
        }

        val createSourcesCode = CodeBlock.builder()
            .add("return listOf(\n")
            .indent()
            .apply {
                for ((index, source) in sources.withIndex()) {
                    if (isConcrete) {
                        add("%L,\n", buildConcreteSource(annotatedClass, source, ctorParams))
                    } else {
                        add(
                            "%L,\n",
                            TypeSpec.anonymousClassBuilder()
                                .superclass(annotatedClass)
                                .applySourceMembers(source, index.toString(), fileProps, isConfigurable, overridden, node)
                                .build(),
                        )
                    }
                }
            }
            .unindent()
            .add(")")
            .build()

        return TypeSpec.classBuilder("ExtensionGenerated")
            .addModifiers(KModifier.INTERNAL)
            .addSuperinterface(sourceFactoryType)
            .addFunction(
                FunSpec.builder("createSources")
                    .addModifiers(KModifier.OVERRIDE)
                    .returns(List::class.asClassName().parameterizedBy(sourceType))
                    .addCode(createSourcesCode)
                    .build(),
            )
            .build()
    }

    private fun validateConcreteSource(
        node: KSClassDeclaration,
        sources: List<SourceDef>,
        overridden: Set<String>,
        ctorParams: Set<String>,
    ) {
        val className = node.simpleName.asString()

        if (sources.any { it.baseUrl.type != "static" }) {
            logger.error(
                "A concrete @Source class ($className) only supports a static baseUrl; " +
                    "make it abstract to use a mirror/custom baseUrl.",
                node,
            )
        }

        if ("id" !in ctorParams || "lang" !in ctorParams) {
            logger.error(
                "A concrete @Source class ($className) must declare `override val lang` and " +
                    "`override val id` as primary constructor parameters.",
                node,
            )
        }

        if ("versionId" in overridden || "versionId" in ctorParams) {
            logger.error(
                "versionId is owned by the DSL; remove `versionId` from $className " +
                    "(set 'versionId = …' in the source { } block if you need a specific value).",
                node,
            )
        }

        if ("name" !in ctorParams && "name" in overridden) {
            logger.warn("name is provided by $className; the DSL name is used for metadata only.", node)
        }
        
        if ("baseUrl" !in ctorParams && "baseUrl" in overridden) {
            logger.warn("baseUrl is provided by $className; the DSL baseUrl is used for metadata/hosts only.", node)
        }
    }

    private fun buildConcreteSource(
        annotatedClass: ClassName,
        source: SourceDef,
        ctorParams: Set<String>,
    ): CodeBlock = buildCodeBlock {
        add("%T(\n", annotatedClass)
        indent()
        if ("name" in ctorParams) add("name = %S,\n", source.name)
        if ("lang" in ctorParams) add("lang = %S,\n", source.lang)
        if ("id" in ctorParams) add("id = %LL,\n", source.id)
        if ("baseUrl" in ctorParams) add("baseUrl = %S,\n", source.baseUrl.defaultUrl)
        unindent()
        add(")")
    }

    private fun TypeSpec.Builder.applySourceMembers(
        source: SourceDef,
        suffix: String,
        fileProps: MutableList<PropertySpec>,
        isConfigurable: Boolean,
        overridden: Set<String>,
        node: KSClassDeclaration,
    ): TypeSpec.Builder = apply {
        val className = node.simpleName.asString()

        if ("name" in overridden) {
            logger.warn("name is provided by $className; skipping generated name (DSL name is used for metadata only)", node)
        } else {
            addProperty(
                PropertySpec.builder("name", String::class.asClassName(), KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addStatement("return %S", source.name).build())
                    .build(),
            )
        }

        if ("lang" in overridden) {
            logger.error("lang is owned by the DSL; remove 'override val lang' from $className", node)
        } else {
            addProperty(
                PropertySpec.builder("lang", String::class.asClassName(), KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addStatement("return %S", source.lang).build())
                    .build(),
            )
        }

        if ("versionId" in overridden) {
            logger.error("versionId is owned by the DSL; remove 'override val versionId' from $className (set 'versionId = …' in the source { } block)", node)
        }

        if ("id" in overridden) {
            logger.error("id is owned by the DSL; remove 'override val id' from $className (set 'id = …' in the source { } block if you need a specific value)", node)
        } else {
            addProperty(
                PropertySpec.builder("id", Long::class.asClassName(), KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addStatement("return %LL", source.id).build())
                    .build(),
            )
        }

        val urlSpec = source.baseUrl
        when {
            "baseUrl" in overridden && urlSpec.type == "static" ->
                logger.warn("baseUrl is provided by $className; skipping generated baseUrl (DSL baseUrl is used for metadata/hosts only)", node)
            "baseUrl" in overridden ->
                logger.error("baseUrl is overridden in $className but the DSL declares a ${urlSpec.type} baseUrl, which is generated.", node)
            urlSpec.type == "static" -> {
                addProperty(
                    PropertySpec.builder("baseUrl", String::class.asClassName(), KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addStatement("return %S", urlSpec.defaultUrl).build())
                        .build(),
                )
            }
            urlSpec.type == "mirrors" -> {
                val strings = stringsForLang(source.lang)
                val prefsName = "mirrorPrefs$suffix"
                val initializer = CodeBlock.builder()
                    .add("%T(\n", mirrorPrefsClass)
                    .indent()
                    .add("preferences = %M(%LL),\n", getPreferencesFn, source.id)
                    .add("mirrors = arrayOf(\n")
                    .indent()
                    .apply {
                        urlSpec.mirrors.forEach { mirror ->
                            add("%S to %S,\n", mirror.label, mirror.url)
                        }
                    }
                    .unindent()
                    .add("),\n")
                    .add("title = %S,\n", strings.mirrorTitle)
                    .unindent()
                    .add(")")
                    .build()
                fileProps += PropertySpec.builder(prefsName, mirrorPrefsClass)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(initializer)
                    .build()
                addProperty(
                    PropertySpec.builder("baseUrl", String::class.asClassName(), KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addStatement("return %N.baseUrl", prefsName).build())
                        .build(),
                )
                addPreferenceScreen(isConfigurable) { addStatement("%N.setupPreferenceScreen(screen)", prefsName) }
                if (!isConfigurable) addSuperinterface(configurable)
            }
            urlSpec.type == "custom" -> {
                val strings = stringsForLang(source.lang)
                val prefsName = "customUrlPrefs$suffix"
                val initializer = CodeBlock.builder()
                    .add("%T(\n", customUrlPrefsClass)
                    .indent()
                    .add("preferences = %M(%LL),\n", getPreferencesFn, source.id)
                    .add("defaultUrl = %S,\n", urlSpec.defaultUrl)
                    .add("title = %S,\n", strings.customUrlTitle)
                    .add("dialogMessage = %S,\n", strings.customUrlDialogMessage)
                    .unindent()
                    .add(")")
                    .build()
                fileProps += PropertySpec.builder(prefsName, customUrlPrefsClass)
                    .addModifiers(KModifier.PRIVATE)
                    .initializer(initializer)
                    .build()
                addProperty(
                    PropertySpec.builder("baseUrl", String::class.asClassName(), KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addStatement("return %N.baseUrl", prefsName).build())
                        .build(),
                )
                addPreferenceScreen(isConfigurable) { addStatement("%N.setupPreferenceScreen(screen)", prefsName) }
                if (!isConfigurable) addSuperinterface(configurable)
            }
        }
    }

    private fun TypeSpec.Builder.addPreferenceScreen(
        callSuper: Boolean,
        addPrefs: FunSpec.Builder.() -> Unit,
    ) {
        addFunction(
            FunSpec.builder("setupPreferenceScreen")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("screen", preferenceScreen)
                .apply(addPrefs)
                .apply { if (callSuper) addStatement("super.setupPreferenceScreen(screen)") }
                .build(),
        )
    }

    class Provider : SymbolProcessorProvider {
        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
            SourceProcessor(environment.codeGenerator, environment.options, environment.logger)
    }
}

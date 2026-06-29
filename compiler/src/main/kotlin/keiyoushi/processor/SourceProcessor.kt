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
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BaseUrlSpecData(
    val type: String,
    val urls: List<String>,
) {
    val defaultUrl: String get() = urls.first()
}

@Serializable
data class SourceDef(
    val name: String,
    val lang: String,
    val id: Long,
    val baseUrl: BaseUrlSpecData,
    val skipCodeGen: Boolean = false,
)

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
        val isConfigurable = annotated.getAllSuperTypes()
            .any { it.declaration.qualifiedName?.asString() == "eu.kanade.tachiyomi.source.ConfigurableSource" }

        val generatedClass = when {
            sources.size == 1 && sources.single().skipCodeGen -> buildPassthroughClass(annotatedClass)
            sources.size == 1 -> buildSingleSourceClass(annotatedClass, sources.single(), isConfigurable)
            else -> buildSourceFactoryClass(annotatedClass, sources, isConfigurable)
        }

        FileSpec.builder(pkg, "ExtensionGenerated")
            .addType(generatedClass)
            .build()
            .writeTo(codeGenerator, Dependencies(false, annotated.containingFile!!))

        return emptyList()
    }

    private fun buildPassthroughClass(annotatedClass: ClassName): TypeSpec =
        TypeSpec.classBuilder("ExtensionGenerated")
            .addModifiers(KModifier.INTERNAL)
            .superclass(annotatedClass)
            .build()

    private fun buildSingleSourceClass(
        annotatedClass: ClassName,
        source: SourceDef,
        isConfigurable: Boolean,
    ): TypeSpec = TypeSpec.classBuilder("ExtensionGenerated")
        .addModifiers(KModifier.INTERNAL)
        .superclass(annotatedClass)
        .applySourceMembers(source, isConfigurable)
        .build()

    private fun buildSourceFactoryClass(
        annotatedClass: ClassName,
        sources: List<SourceDef>,
        isConfigurable: Boolean,
    ): TypeSpec {
        val sourceFactoryType = ClassName("eu.kanade.tachiyomi.source", "SourceFactory")
        val sourceType = ClassName("eu.kanade.tachiyomi.source", "Source")

        val createSourcesCode = CodeBlock.builder()
            .add("return listOf(\n")
            .indent()
            .apply {
                for (source in sources) {
                    add(
                        "%L,\n",
                        TypeSpec.anonymousClassBuilder()
                            .superclass(annotatedClass)
                            .applySourceMembers(source, isConfigurable)
                            .build(),
                    )
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

    private fun TypeSpec.Builder.applySourceMembers(source: SourceDef, isConfigurable: Boolean): TypeSpec.Builder = apply {
        addProperty(
            PropertySpec.builder("name", String::class.asClassName(), KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addStatement("return %S", source.name).build())
                .build(),
        )
        addProperty(
            PropertySpec.builder("lang", String::class.asClassName(), KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addStatement("return %S", source.lang).build())
                .build(),
        )
        addProperty(
            PropertySpec.builder("id", Long::class.asClassName(), KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addStatement("return %LL", source.id).build())
                .build(),
        )

        val urlSpec = source.baseUrl
        when (urlSpec.type) {
            "static" -> {
                addProperty(
                    PropertySpec.builder("baseUrl", String::class.asClassName(), KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addStatement("return %S", urlSpec.defaultUrl).build())
                        .build(),
                )
            }
            "mirrors" -> {
                val mirrorsArg = CodeBlock.builder().apply {
                    urlSpec.urls.forEachIndexed { i, url ->
                        if (i > 0) add(", ")
                        add("%S", url)
                    }
                }.build()
                addProperty(
                    PropertySpec.builder("mirrorPrefs", mirrorPrefsClass)
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            CodeBlock.of(
                                "lazy { %T(%M(id), arrayOf(%L)) }",
                                mirrorPrefsClass, getPreferencesFn, mirrorsArg,
                            ),
                        ).build(),
                )
                addProperty(
                    PropertySpec.builder("baseUrl", String::class.asClassName(), KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addStatement("return mirrorPrefs.baseUrl").build())
                        .build(),
                )
                addPreferenceScreen(isConfigurable) { addStatement("mirrorPrefs.setupPreferenceScreen(screen)") }
                if (!isConfigurable) addSuperinterface(configurable)
            }
            "custom" -> {
                addProperty(
                    PropertySpec.builder("customUrlPrefs", customUrlPrefsClass)
                        .addModifiers(KModifier.PRIVATE)
                        .delegate(
                            CodeBlock.of(
                                "lazy { %T(%M(id), %S) }",
                                customUrlPrefsClass, getPreferencesFn, urlSpec.defaultUrl,
                            ),
                        ).build(),
                )
                addProperty(
                    PropertySpec.builder("baseUrl", String::class.asClassName(), KModifier.OVERRIDE)
                        .getter(FunSpec.getterBuilder().addStatement("return customUrlPrefs.baseUrl").build())
                        .build(),
                )
                addPreferenceScreen(isConfigurable) { addStatement("customUrlPrefs.setupPreferenceScreen(screen)") }
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

package keiyoushi.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import keiyoushi.lint.inClass
import keiyoushi.lint.issue
import keiyoushi.lint.loc
import keiyoushi.lint.stringValue
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UReferenceExpression

class JsonDetector :
    Detector(),
    SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UClass::class.java, UField::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = object : UElementHandler() {
        override fun visitClass(node: UClass) {
            val ktClass = node.sourcePsi as? KtClass ?: return
            if (!ktClass.hasAnnotation("Serializable")) return

            if (ktClass.isData()) {
                val dataKeyword = ktClass.modifierList?.getModifier(org.jetbrains.kotlin.lexer.KtTokens.DATA_KEYWORD)
                context.report(
                    DATA_CLASS_DTO,
                    node,
                    context.getLocation(dataKeyword ?: ktClass.nameIdentifier ?: ktClass),
                    "Use a regular `class` for `@Serializable` DTOs; `data class` generates code that's never used",
                    dataKeyword?.let { LintFix.create().replace().range(context.getLocation(it)).text("data ").with("").build() },
                )
            }

            val declarations = ktClass.primaryConstructorParameters.filter { it.hasValOrVar() } +
                ktClass.getProperties().filter { it.hasBackingFieldInitializer() }
            declarations.forEach { checkProperty(context, it) }
        }

        override fun visitField(node: UField) {
            val property = node.sourcePsi as? KtProperty ?: return
            val delegate = property.delegateExpression?.text ?: return
            val type = property.typeReference?.text ?: return
            if (delegate.startsWith("injectLazy") && (type == "Json" || type == "ProtoBuf")) {
                context.report(
                    LOCAL_JSON,
                    node,
                    context.getLocation(property),
                    "Use `parseAs`/`toJsonString`/`parseAsProto` with the shared instance instead of injecting a local `$type`",
                )
            }
        }
    }

    private fun KtProperty.hasBackingFieldInitializer() = initializer == null && delegate == null && getter == null

    private fun checkProperty(context: JavaContext, declaration: KtNamedDeclaration) {
        val name = declaration.name ?: return
        val serialName = (declaration as KtAnnotated).annotationEntries.firstOrNull { it.shortName?.asString() == "SerialName" }
        val serialValue = serialName?.valueArguments?.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression

        if (serialName != null && serialValue != null && serialValue.entries.joinToString("") { it.text } == name) {
            context.report(
                REDUNDANT_SERIAL_NAME,
                serialName,
                context.getLocation(serialName as PsiElement),
                "`@SerialName(\"$name\")` matches the property name and can be removed",
                LintFix.create().replace().range(context.getLocation(serialName as PsiElement)).all().with("").reformat(true).build(),
            )
        }

        if ('_' in name.trim('_')) {
            context.report(
                SNAKE_CASE_FIELD,
                declaration,
                context.getLocation(declaration.nameIdentifier ?: declaration),
                "Use a camelCase property with `@SerialName(\"$name\")`",
            )
        }

        if (declaration is KtParameter) {
            val type = declaration.typeReference?.text ?: return
            if (type.endsWith("?") && declaration.defaultValue?.text == "null") {
                context.report(
                    NULL_DEFAULT,
                    declaration,
                    context.getLocation(declaration.defaultValue!!),
                    "No need for `= null`: the app's `Json` decodes a missing nullable field as null (keep it only if the class is also built in code)",
                    LintFix.create().replace().pattern("""\s*=\s*null""").with("").range(context.getLocation(declaration)).build(),
                )
            }
        }
    }

    override fun getApplicableMethodNames(): List<String> = listOf(
        "decodeFromString",
        "decodeFromStream",
        "decodeFromJsonElement",
        "toMediaType",
        "toMediaTypeOrNull",
    )

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        when (method.name) {
            "decodeFromString", "decodeFromStream", "decodeFromJsonElement" ->
                if (method.inClass("kotlinx.serialization.json.Json", "kotlinx.serialization.json.JvmStreamsKt")) {
                    context.report(
                        DECODE_FROM_STRING,
                        node,
                        context.loc(node),
                        "Use `keiyoushi.utils.parseAs<T>()` on the `Response`, `String` or `JsonElement`",
                    )
                }

            "toMediaType", "toMediaTypeOrNull" -> {
                val receiver = (node.receiver ?: return).stringValue() ?: return
                if (receiver.startsWith("application/json")) {
                    context.report(
                        JSON_MEDIA_TYPE,
                        node,
                        context.loc(node),
                        "Use `toJsonRequestBody()` on a `@Serializable` request object instead of building a JSON media type",
                    )
                }
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = listOf("jsonPrimitive", "jsonObject", "jsonArray")

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement) {
        val owner = (referenced as? PsiMember)?.containingClass?.qualifiedName ?: return
        if (owner != "kotlinx.serialization.json.JsonElementKt") return
        context.report(
            MANUAL_JSON_TRAVERSAL,
            reference,
            context.getLocation(reference),
            "Parse into a `@Serializable` class with `parseAs<T>()`, or use the `keiyoushi.utils` accessors (`obj`, `string`, `stringOrNull`, ...)",
        )
    }

    companion object {
        private fun KtClass.hasAnnotation(shortName: String) = annotationEntries.any { it: KtAnnotationEntry -> it.shortName?.asString() == shortName }

        val DATA_CLASS_DTO = issue<JsonDetector>(
            "KeiDataClassDto",
            "`data class` used for a DTO",
            """
            `@Serializable` DTOs should be regular classes: `data class` generates `copy`, `componentN`, `equals`, \
            `hashCode` and `toString`, which bloat every extension and are never used.
            """,
        )

        val REDUNDANT_SERIAL_NAME = issue<JsonDetector>(
            "KeiRedundantSerialName",
            "`@SerialName` equal to the property name",
            "Only use `@SerialName` when the JSON key differs from the Kotlin property name.",
        )

        val SNAKE_CASE_FIELD = issue<JsonDetector>(
            "KeiSnakeCaseField",
            "snake_case DTO property",
            "DTO properties are camelCase; map the JSON key with `@SerialName(\"snake_key\")`.",
        )

        val NULL_DEFAULT = issue<JsonDetector>(
            "KeiNullDefault",
            "`= null` default on a nullable DTO field",
            """
            The app's `Json` uses `explicitNulls = false`, so a missing nullable field already decodes as null. \
            Mandatory fields (ids, titles, slugs) must not be nullable or defaulted at all, so broken entries fail early.
            """,
        )

        val LOCAL_JSON = issue<JsonDetector>(
            "KeiLocalJsonInstance",
            "Local `Json`/`ProtoBuf` instance",
            "Use the `keiyoushi.utils` helpers (`parseAs`, `toJsonString`, `toJsonRequestBody`, `parseAsProto`), which use the shared instances.",
        )

        val DECODE_FROM_STRING = issue<JsonDetector>(
            "KeiDecodeFromString",
            "`Json.decodeFrom*` instead of `parseAs`",
            "`parseAs<T>()` works on `Response`, `String`, `InputStream` and `JsonElement` with the shared instance.",
        )

        val JSON_MEDIA_TYPE = issue<JsonDetector>(
            "KeiJsonMediaType",
            "JSON media type built by hand",
            """
            Use `toJsonRequestBody()` on a `@Serializable` request object (no `buildJsonObject`, no \
            `"application/json".toMediaType()`; `application/json` is UTF-8 by definition).
            """,
        )

        val MANUAL_JSON_TRAVERSAL = issue<JsonDetector>(
            "KeiManualJsonTraversal",
            "Manual `JsonElement` traversal",
            """
            Define `@Serializable` classes and use `parseAs<T>()`. For a quick lookup, the `keiyoushi.utils` \
            accessors (`element["key"]?.stringOrNull`, `obj`, `array`, ...) replace `jsonObject`/`jsonPrimitive` chains.
            """,
        )

        val ISSUES = listOf(
            DATA_CLASS_DTO,
            REDUNDANT_SERIAL_NAME,
            SNAKE_CASE_FIELD,
            NULL_DEFAULT,
            LOCAL_JSON,
            DECODE_FROM_STRING,
            JSON_MEDIA_TYPE,
            MANUAL_JSON_TRAVERSAL,
        )
    }
}

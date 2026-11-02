package com.code2request.parser

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

/**
 * Resolves PSI class types to their fields for request body sample generation.
 * Supports regular classes, Java Records, and Lombok-annotated classes.
 */
object PsiTypeResolver {

    data class TypeField(
        val name: String,
        val type: String,
        val psiType: PsiType? = null,
        /** Key used in JSON bodies; differs from [name] when @JsonProperty / @JSONField / @SerializedName is present. */
        val jsonName: String = name,
    )

    /** JSON naming annotations and the attribute that carries the name. */
    private val JSON_NAME_ANNOTATIONS = listOf(
        "com.fasterxml.jackson.annotation.JsonProperty" to "value",
        "com.alibaba.fastjson.annotation.JSONField" to "name",
        "com.alibaba.fastjson2.annotation.JSONField" to "name",
        "com.google.gson.annotations.SerializedName" to "value",
    )

    private const val JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore"

    private fun jsonNameOf(owner: PsiModifierListOwner, default: String): String =
        explicitJsonName(owner) ?: default

    /** The name given by a JSON naming annotation on [owner], or null if there is none. */
    private fun explicitJsonName(owner: PsiModifierListOwner?): String? {
        if (owner == null) return null
        for ((fqn, attr) in JSON_NAME_ANNOTATIONS) {
            val ann = owner.getAnnotation(fqn) ?: continue
            val value = (ann.findDeclaredAttributeValue(attr) as? PsiLiteralExpression)?.value as? String
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun isJsonIgnored(owner: PsiModifierListOwner?): Boolean =
        owner != null && owner.hasAnnotation(JSON_IGNORE)

    /**
     * Places a JSON annotation for a field may live, besides the field itself:
     * - the constructor parameter of the same name: Kotlin puts `@JsonProperty` on a
     *   `val` in the primary constructor onto the parameter, not the field;
     * - the getter: Java code sometimes annotates `getXxx()` instead of the field.
     */
    private class FieldAnnotationSources(psiClass: PsiClass) {
        private val ctorParams: Map<String, PsiParameter> =
            psiClass.constructors
                .maxByOrNull { it.parameterList.parametersCount }
                ?.parameterList?.parameters
                ?.associateBy { it.name }
                ?: emptyMap()

        private val cls = psiClass

        fun related(fieldName: String): List<PsiModifierListOwner> {
            val cap = fieldName.replaceFirstChar { it.uppercase() }
            val getter = (cls.findMethodsByName("get$cap", true).asList() +
                cls.findMethodsByName("is$cap", true).asList())
                .firstOrNull { it.parameterList.parametersCount == 0 }
            return listOfNotNull(ctorParams[fieldName], getter)
        }
    }

    fun resolveFields(psiClass: PsiClass, visited: MutableSet<String> = mutableSetOf()): List<TypeField> {
        val fqn = psiClass.qualifiedName ?: return emptyList()
        if (fqn in visited) return emptyList()
        visited.add(fqn)

        // 1. Java Record: use record components directly
        if (psiClass.isRecord) {
            val components = psiClass.recordComponents
            if (components.isNotEmpty()) {
                return components
                    .filter { !isJsonIgnored(it) }
                    .map {
                        TypeField(name = it.name, type = it.type.presentableText, psiType = it.type,
                            jsonName = jsonNameOf(it, it.name))
                    }
            }
        }

        // 2. Regular fields (excludes static, transient and @JsonIgnore)
        val sources = FieldAnnotationSources(psiClass)
        val fields = psiClass.allFields
            .filter { !it.hasModifierProperty(PsiModifier.STATIC) }
            .filter { !it.hasModifierProperty(PsiModifier.TRANSIENT) }
            .mapNotNull { field ->
                val related = sources.related(field.name)
                if (isJsonIgnored(field) || related.any { isJsonIgnored(it) }) return@mapNotNull null
                val jsonName = explicitJsonName(field)
                    ?: related.firstNotNullOfOrNull { explicitJsonName(it) }
                    ?: field.name
                TypeField(name = field.name, type = field.type.presentableText, psiType = field.type,
                    jsonName = jsonName)
            }

        if (fields.isNotEmpty()) return fields

        // 3. Fallback: infer from getter methods (Lombok, etc.)
        return inferFieldsFromGetters(psiClass)
    }

    /**
     * When allFields is empty (e.g. Lombok @Data without delombok),
     * infer field names/types from getter methods like getXxx() / isXxx().
     */
    private fun inferFieldsFromGetters(psiClass: PsiClass): List<TypeField> {
        val seen = mutableSetOf<String>()
        return psiClass.allMethods
            .filter { !it.hasModifierProperty(PsiModifier.STATIC) }
            .filter { it.parameterList.parametersCount == 0 }
            .filter { it.returnType != null && it.returnType != PsiType.VOID }
            .mapNotNull { method ->
                val name = method.name
                val fieldName = when {
                    name.startsWith("get") && name.length > 3 ->
                        name.substring(3).replaceFirstChar { it.lowercase() }
                    name.startsWith("is") && name.length > 2
                        && (method.returnType == PsiType.BOOLEAN
                            || method.returnType?.canonicalText == "java.lang.Boolean") ->
                        name.substring(2).replaceFirstChar { it.lowercase() }
                    else -> null
                }
                if (fieldName != null && fieldName !in EXCLUDED_GETTER_NAMES
                    && !isJsonIgnored(method) && seen.add(fieldName)) {
                    val returnType = method.returnType!!
                    TypeField(name = fieldName, type = returnType.presentableText, psiType = returnType,
                        jsonName = jsonNameOf(method, fieldName))
                } else null
            }
    }

    fun resolveClass(psiType: PsiType): PsiClass? = when (psiType) {
        is PsiClassType -> psiType.resolve()
        else -> null
    }

    fun findClass(typeName: String, project: Project): PsiClass? {
        val cleanName = typeName.substringBefore("<")
        return JavaPsiFacade.getInstance(project)
            .findClass(cleanName, GlobalSearchScope.allScope(project))
    }

    fun isSimpleType(name: String): Boolean = name in SIMPLE_TYPES

    // Compare the raw type name exactly: startsWith("Set") would also match a DTO named "SettingDto".
    private val LIST_TYPES = setOf(
        "List", "ArrayList", "LinkedList", "Set", "HashSet", "LinkedHashSet", "TreeSet",
        "Collection", "Iterable",
    )
    private val MAP_TYPES = setOf("Map", "HashMap", "LinkedHashMap", "TreeMap", "ConcurrentHashMap")

    private fun rawSimpleName(name: String): String =
        name.substringBefore("<").substringAfterLast(".").trim()

    fun isList(name: String): Boolean = rawSimpleName(name) in LIST_TYPES

    fun isMap(name: String): Boolean = rawSimpleName(name) in MAP_TYPES

    /** Getter names from Object/common base that should not be treated as DTO fields. */
    private val EXCLUDED_GETTER_NAMES = setOf(
        "class", "hashCode", "string",  // getClass(), hashCode() (if boxed), toString()
    )

    private val SIMPLE_TYPES = setOf(
        "String", "java.lang.String",
        "int", "Integer", "java.lang.Integer",
        "long", "Long", "java.lang.Long",
        "short", "Short", "java.lang.Short",
        "byte", "Byte", "java.lang.Byte",
        "boolean", "Boolean", "java.lang.Boolean",
        "double", "Double", "java.lang.Double",
        "float", "Float", "java.lang.Float",
        "char", "Character", "java.lang.Character",
        "BigDecimal", "java.math.BigDecimal",
        "BigInteger", "java.math.BigInteger",
        "Date", "java.util.Date",
        "LocalDate", "java.time.LocalDate",
        "LocalDateTime", "java.time.LocalDateTime",
        "LocalTime", "java.time.LocalTime",
        "Instant", "java.time.Instant",
        "UUID", "java.util.UUID"
    )
}

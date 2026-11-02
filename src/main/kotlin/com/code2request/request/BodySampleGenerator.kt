package com.code2request.request

import com.code2request.parser.PsiTypeResolver
import com.google.gson.GsonBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.*

/**
 * Generates sample JSON bodies for @RequestBody parameters.
 * Recursively expands DTO fields using PSI type resolution.
 */
object BodySampleGenerator {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun generateSampleFromPsiType(psiType: PsiType, project: Project): String {
        val value = buildSampleFromType(psiType, project, emptySet())
        return gson.toJson(value)
    }

    fun generateSampleByTypeName(typeName: String, project: Project): String {
        val psiClass = PsiTypeResolver.findClass(typeName, project)
        val value = if (psiClass != null) {
            buildSampleFromClass(psiClass, project, emptySet())
        } else {
            buildSimpleSample(typeName.substringAfterLast("."))
        }
        return gson.toJson(value)
    }

    /**
     * [visited] holds the classes on the current path only (not siblings), so two fields
     * of the same DTO type are both expanded, while self-references still stop recursion.
     */
    private fun buildSampleFromType(
        psiType: PsiType,
        project: Project,
        visited: Set<String>,
        fieldName: String? = null,
    ): Any {
        return when {
            psiType is PsiPrimitiveType -> {
                if (fieldName != null) {
                    SmartSampleValues.forName(fieldName, psiType.presentableText) ?: primitiveSample(psiType)
                } else primitiveSample(psiType)
            }
            // Arrays: one sample element, e.g. Long[] ids -> [1]
            psiType is PsiArrayType ->
                listOf(buildSampleFromType(psiType.componentType, project, visited, fieldName))
            psiType is PsiClassType -> {
                val text = psiType.presentableText
                val resolved = psiType.resolve()
                val fqn = resolved?.qualifiedName ?: text

                when {
                    PsiTypeResolver.isSimpleType(fqn) || PsiTypeResolver.isSimpleType(text) -> {
                        val typeName = text.substringBefore("<")
                        if (fieldName != null) {
                            SmartSampleValues.forName(fieldName, typeName) ?: buildSimpleSample(typeName)
                        } else buildSimpleSample(typeName)
                    }
                    // Collections: one sample element, e.g. List<OrderItem> items -> [{...}]
                    PsiTypeResolver.isList(text) -> {
                        val elementType = psiType.parameters.firstOrNull()
                        if (elementType == null) emptyList<Any>()
                        else listOf(buildSampleFromType(elementType, project, visited, fieldName))
                    }
                    // Maps: one sample entry, e.g. Map<String, Integer> -> {"key": 1}
                    PsiTypeResolver.isMap(text) -> {
                        val valueType = psiType.parameters.getOrNull(1)
                        if (valueType == null) emptyMap<String, Any>()
                        else mapOf("key" to buildSampleFromType(valueType, project, visited))
                    }
                    resolved?.isEnum == true -> enumSample(resolved)
                    resolved != null -> buildSampleFromClass(resolved, project, visited)
                    else -> emptyMap<String, Any>()
                }
            }
            // Wildcards such as List<? extends Foo>
            psiType is PsiWildcardType ->
                psiType.bound?.let { buildSampleFromType(it, project, visited, fieldName) } ?: ""
            else -> ""
        }
    }

    private fun buildSampleFromClass(cls: PsiClass, project: Project, visited: Set<String>): Any {
        val fqn = cls.qualifiedName ?: return emptyMap<String, Any>()
        if (fqn in visited) return emptyMap<String, Any>()
        val path = visited + fqn

        val fields = PsiTypeResolver.resolveFields(cls)
        if (fields.isEmpty()) return emptyMap<String, Any>()

        // Keep declaration order in the generated JSON
        val result = LinkedHashMap<String, Any>()
        fields.forEach { field ->
            result[field.jsonName] =
                field.psiType?.let { buildSampleFromType(it, project, path, fieldName = field.name) } ?: ""
        }
        return result
    }

    private fun buildSimpleSample(typeName: String): Any = when {
        typeName in setOf("String") -> "test"
        typeName in setOf("int", "Integer", "short", "Short", "byte", "Byte") -> 1
        typeName in setOf("long", "Long") -> 1
        typeName in setOf("boolean", "Boolean") -> true
        typeName in setOf("double", "Double", "float", "Float") -> 0.0
        typeName in setOf("BigDecimal", "BigInteger") -> 0
        typeName in setOf("char", "Character") -> "a"
        typeName in setOf("Date", "LocalDate") -> "2024-01-01"
        typeName in setOf("LocalDateTime") -> "2024-01-01T00:00:00"
        typeName in setOf("LocalTime") -> "00:00:00"
        typeName in setOf("Instant") -> "2024-01-01T00:00:00Z"
        typeName in setOf("UUID") -> "00000000-0000-0000-0000-000000000000"
        else -> ""
    }

    private fun primitiveSample(type: PsiPrimitiveType): Any = when (type) {
        PsiTypes.intType(), PsiTypes.shortType(), PsiTypes.byteType() -> 1
        PsiTypes.longType() -> 1
        PsiTypes.booleanType() -> true
        PsiTypes.doubleType(), PsiTypes.floatType() -> 0.0
        PsiTypes.charType() -> "a"
        else -> 0
    }

    private fun enumSample(cls: PsiClass): String =
        cls.fields.filterIsInstance<PsiEnumConstant>().firstOrNull()?.name ?: "ENUM_VALUE"
}

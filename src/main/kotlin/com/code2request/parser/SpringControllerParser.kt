package com.code2request.parser

import com.code2request.model.*
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

/**
 * Parses Spring MVC / Spring Boot controller methods via PSI.
 * Supports @GetMapping, @PostMapping, @PutMapping, @DeleteMapping, @PatchMapping, @RequestMapping.
 * Extracts @PathVariable, @RequestParam, @RequestHeader, @RequestBody parameters.
 */
object SpringControllerParser {

    private val CONTROLLER_ANNOTATIONS = setOf(
        "RestController", "Controller",
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.stereotype.Controller"
    )

    private val MAPPING_SHORT_NAMES = mapOf(
        "GetMapping" to HttpMethod.GET,
        "PostMapping" to HttpMethod.POST,
        "PutMapping" to HttpMethod.PUT,
        "DeleteMapping" to HttpMethod.DELETE,
        "PatchMapping" to HttpMethod.PATCH
    )

    fun isControllerMethod(psiMethod: PsiMethod): Boolean {
        val cls = psiMethod.containingClass ?: return false
        if (!isController(cls)) return false
        return findMappingAnnotation(psiMethod) != null
    }

    fun parseMethod(psiMethod: PsiMethod): EndpointModel? {
        val cls = psiMethod.containingClass ?: return null
        if (!isController(cls)) return null

        val (httpMethod, methodPath) = findMappingAnnotation(psiMethod) ?: return null
        val classPath = getClassPath(cls)
        val params = extractParams(psiMethod)
        val requestBodyClass = findRequestBodyType(psiMethod)
        val module = ModuleUtil.findModuleForPsiElement(psiMethod)?.name ?: ""

        return EndpointModel(
            module = module,
            controllerClass = cls.qualifiedName ?: cls.name ?: "",
            methodName = psiMethod.name,
            httpMethod = httpMethod,
            classPath = classPath,
            methodPath = methodPath,
            params = params,
            requestBodyClass = requestBodyClass
        )
    }

    private fun isController(cls: PsiClass): Boolean =
        cls.annotations.any { ann ->
            val fqn = ann.qualifiedName ?: ann.nameReferenceElement?.text ?: ""
            fqn in CONTROLLER_ANNOTATIONS || fqn.substringAfterLast(".") in setOf("RestController", "Controller")
        }

    private fun findMappingAnnotation(method: PsiMethod): Pair<HttpMethod, String>? {
        for (ann in method.annotations) {
            val fqn = ann.qualifiedName ?: ann.nameReferenceElement?.text ?: continue
            val short = fqn.substringAfterLast(".")

            MAPPING_SHORT_NAMES[short]?.let { httpMethod ->
                return Pair(httpMethod, getAnnotationPath(ann))
            }

            if (short == "RequestMapping" || fqn.endsWith(".RequestMapping")) {
                val httpMethod = parseRequestMappingMethod(ann.findAttributeValue("method"))
                return Pair(httpMethod, getAnnotationPath(ann))
            }
        }
        return null
    }

    private fun parseRequestMappingMethod(attr: PsiAnnotationMemberValue?): HttpMethod {
        val text = attr?.text ?: return HttpMethod.GET
        return when {
            "POST" in text -> HttpMethod.POST
            "PUT" in text -> HttpMethod.PUT
            "DELETE" in text -> HttpMethod.DELETE
            "PATCH" in text -> HttpMethod.PATCH
            else -> HttpMethod.GET
        }
    }

    private fun getClassPath(cls: PsiClass): String {
        val ann = cls.annotations.find { ann ->
            val fqn = ann.qualifiedName ?: ann.nameReferenceElement?.text ?: ""
            fqn.endsWith("RequestMapping")
        } ?: return ""
        return getAnnotationPath(ann)
    }

    private fun getAnnotationPath(ann: PsiAnnotation): String {
        val value = ann.findAttributeValue("value") ?: ann.findAttributeValue("path") ?: return ""
        return extractStringValue(value) ?: ""
    }

    private fun extractStringValue(value: PsiAnnotationMemberValue?): String? = when (value) {
        is PsiLiteralExpression -> value.value as? String
        is PsiArrayInitializerMemberValue -> extractStringValue(value.initializers.firstOrNull())
        else -> value?.text?.trim('"')?.takeIf { it.isNotEmpty() && !it.contains("\\") }
    }

    private fun extractParams(method: PsiMethod): List<ParamModel> =
        method.parameterList.parameters.flatMap { extractParamList(it) }

    /**
     * Returns a list of ParamModels for a single PsiParameter.
     * For DTO query objects (unannotated complex types / @ModelAttribute),
     * the DTO fields are expanded into individual query params.
     */
    private fun extractParamList(param: PsiParameter): List<ParamModel> {
        // Check for explicit Spring annotations first
        for (ann in param.annotations) {
            val fqn = ann.qualifiedName ?: ann.nameReferenceElement?.text ?: continue
            val short = fqn.substringAfterLast(".")

            when (short) {
                "PathVariable" -> {
                    val name = annotationString(ann, "value")
                        ?: annotationString(ann, "name")
                        ?: param.name
                    return listOf(ParamModel(name = name, type = param.type.presentableText,
                        required = true, source = ParamSource.PATH))
                }
                "RequestParam" -> {
                    val name = annotationString(ann, "value")
                        ?: annotationString(ann, "name")
                        ?: param.name
                    val required = annotationBoolean(ann, "required") ?: true
                    // MultipartFile with @RequestParam should be treated as file upload
                    if (isMultipartFileType(param.type.canonicalText)) {
                        return listOf(ParamModel(name = name, type = param.type.presentableText,
                            required = required, source = ParamSource.FORM_DATA, isFile = true))
                    }
                    val default = annotationString(ann, "defaultValue")
                        ?.takeIf { !it.contains("\ue000") } // Spring's ValueConstants.DEFAULT_NONE
                    return listOf(ParamModel(name = name, type = param.type.presentableText,
                        required = required, source = ParamSource.QUERY, defaultValue = default))
                }
                "RequestHeader" -> {
                    val name = annotationString(ann, "value")
                        ?: annotationString(ann, "name")
                        ?: param.name
                    val required = annotationBoolean(ann, "required") ?: true
                    return listOf(ParamModel(name = name, type = param.type.presentableText,
                        required = required, source = ParamSource.HEADER))
                }
                "RequestBody" -> return emptyList() // handled separately
                "RequestPart" -> {
                    val name = annotationString(ann, "value")
                        ?: annotationString(ann, "name")
                        ?: param.name
                    val required = annotationBoolean(ann, "required") ?: true
                    val isFile = isMultipartFileType(param.type.canonicalText)
                    return listOf(ParamModel(name = name, type = param.type.presentableText,
                        required = required, source = ParamSource.FORM_DATA, isFile = isFile))
                }
                "ModelAttribute" -> {
                    // Expand DTO fields as query params
                    return expandDtoFields(param)
                }
            }
        }

        // No Spring annotation
        val canonical = param.type.canonicalText

        // Bare MultipartFile
        if (isMultipartFileType(canonical)) {
            return listOf(ParamModel(name = param.name, type = param.type.presentableText,
                required = true, source = ParamSource.FORM_DATA, isFile = true))
        }

        // Simple type → implicit @RequestParam
        if (isImplicitRequestParam(canonical)) {
            return listOf(ParamModel(name = param.name, type = param.type.presentableText,
                required = false, source = ParamSource.QUERY))
        }

        // Framework type → skip
        if (canonical in FRAMEWORK_TYPES || canonical.substringAfterLast(".") in FRAMEWORK_TYPES) {
            return emptyList()
        }

        // Complex type without annotation → Spring binds as query parameter object (DTO)
        // Expand its fields into individual query params
        return expandDtoFields(param)
    }

    /**
     * Expand a DTO/Record/POJO's fields into individual query ParamModels.
     * Spring binds each field of the object from query parameters.
     */
    private fun expandDtoFields(param: PsiParameter): List<ParamModel> {
        val psiType = param.type
        if (psiType !is PsiClassType) {
            return listOf(ParamModel(name = param.name, type = psiType.presentableText,
                required = false, source = ParamSource.QUERY))
        }
        val psiClass = psiType.resolve() ?: return listOf(
            ParamModel(name = param.name, type = psiType.presentableText,
                required = false, source = ParamSource.QUERY)
        )

        val fields = PsiTypeResolver.resolveFields(psiClass)
        if (fields.isEmpty()) {
            return listOf(ParamModel(name = param.name, type = psiType.presentableText,
                required = false, source = ParamSource.QUERY))
        }

        return fields
            .filter { PsiTypeResolver.isSimpleType(it.type) || PsiTypeResolver.isSimpleType(it.psiType?.canonicalText ?: "") }
            .map { field ->
                ParamModel(
                    name = field.name,
                    type = field.type,
                    required = false,
                    source = ParamSource.QUERY,
                )
            }
            .ifEmpty {
                // If no simple-type fields found, still list all fields
                fields.map { field ->
                    ParamModel(
                        name = field.name,
                        type = field.type,
                        required = false,
                        source = ParamSource.QUERY,
                    )
                }
            }
    }

    private fun isMultipartFileType(canonicalText: String): Boolean =
        "MultipartFile" in canonicalText

    /** Types that Spring auto-injects — NOT user request parameters. */
    private val FRAMEWORK_TYPES = setOf(
        "HttpServletRequest", "javax.servlet.http.HttpServletRequest",
        "jakarta.servlet.http.HttpServletRequest",
        "HttpServletResponse", "javax.servlet.http.HttpServletResponse",
        "jakarta.servlet.http.HttpServletResponse",
        "HttpSession", "javax.servlet.http.HttpSession",
        "jakarta.servlet.http.HttpSession",
        "Model", "org.springframework.ui.Model",
        "ModelMap", "org.springframework.ui.ModelMap",
        "ModelAndView", "org.springframework.web.servlet.ModelAndView",
        "BindingResult", "org.springframework.validation.BindingResult",
        "Errors", "org.springframework.validation.Errors",
        "RedirectAttributes", "org.springframework.web.servlet.mvc.support.RedirectAttributes",
        "UriComponentsBuilder", "org.springframework.web.util.UriComponentsBuilder",
        "Principal", "java.security.Principal",
        "Locale", "java.util.Locale",
        "TimeZone", "java.util.TimeZone",
        "InputStream", "java.io.InputStream",
        "OutputStream", "java.io.OutputStream",
        "Reader", "java.io.Reader",
        "Writer", "java.io.Writer",
        "ServerHttpRequest", "org.springframework.http.server.ServerHttpRequest",
        "ServerHttpResponse", "org.springframework.http.server.ServerHttpResponse",
        "Authentication", "org.springframework.security.core.Authentication",
    )

    private val IMPLICIT_SIMPLE_TYPES = setOf(
        "String", "java.lang.String",
        "int", "Integer", "java.lang.Integer",
        "long", "Long", "java.lang.Long",
        "short", "Short", "java.lang.Short",
        "byte", "Byte", "java.lang.Byte",
        "boolean", "Boolean", "java.lang.Boolean",
        "double", "Double", "java.lang.Double",
        "float", "Float", "java.lang.Float",
        "BigDecimal", "java.math.BigDecimal",
        "BigInteger", "java.math.BigInteger",
        "Date", "java.util.Date",
        "LocalDate", "java.time.LocalDate",
        "LocalDateTime", "java.time.LocalDateTime",
        "UUID", "java.util.UUID",
    )

    private fun isImplicitRequestParam(canonicalText: String): Boolean {
        if (canonicalText in FRAMEWORK_TYPES) return false
        return canonicalText in IMPLICIT_SIMPLE_TYPES
    }

    fun findRequestBodyType(method: PsiMethod): String? =
        method.parameterList.parameters.firstOrNull { param ->
            param.annotations.any { ann ->
                val fqn = ann.qualifiedName ?: ann.nameReferenceElement?.text ?: ""
                fqn.endsWith("RequestBody")
            }
        }?.type?.canonicalText

    fun findRequestBodyParameter(method: PsiMethod): PsiParameter? =
        method.parameterList.parameters.firstOrNull { param ->
            param.annotations.any { ann ->
                val fqn = ann.qualifiedName ?: ann.nameReferenceElement?.text ?: ""
                fqn.endsWith("RequestBody")
            }
        }

    private fun annotationString(ann: PsiAnnotation, attr: String): String? =
        extractStringValue(ann.findAttributeValue(attr))?.takeIf { it.isNotEmpty() }

    private fun annotationBoolean(ann: PsiAnnotation, attr: String): Boolean? =
        when (ann.findAttributeValue(attr)?.text) {
            "true" -> true
            "false" -> false
            else -> null
        }
}

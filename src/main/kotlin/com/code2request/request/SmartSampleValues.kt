package com.code2request.request

/**
 * Generates contextual sample values based on parameter/field name and type.
 *
 * When the name matches a known pattern (e.g. "email", "phone", "age"),
 * a meaningful example is returned instead of a generic placeholder like "test".
 */
object SmartSampleValues {

    // ----------------------------------------------------------------
    // Name → sample value mapping (checked against lowercase name)
    // Order matters: first match wins.
    // ----------------------------------------------------------------

    private data class Rule(
        val keywords: List<String>,
        val matchMode: MatchMode,
        val sample: Any,
        /** Only apply when the resolved type family matches (null = any type) */
        val typeFamily: TypeFamily? = null,
    )

    private enum class MatchMode { EXACT, CONTAINS, SUFFIX, PREFIX }

    /** Coarse type buckets so name rules can be type-aware. */
    enum class TypeFamily { STRING, INTEGER, LONG, FLOAT, BOOLEAN, DATE, DATETIME, OTHER }

    private val rules: List<Rule> = listOf(
        // ---- identity / account ----
        // Long compound words are safe with CONTAINS; short words (mail, tel, pwd) use EXACT
        Rule(listOf("email", "user_email", "userEmail"), MatchMode.CONTAINS, "user@example.com", TypeFamily.STRING),
        Rule(listOf("mail"), MatchMode.EXACT, "user@example.com", TypeFamily.STRING),
        Rule(listOf("phone", "mobile", "cellphone", "telephone"), MatchMode.CONTAINS, "13800138000", TypeFamily.STRING),
        Rule(listOf("tel"), MatchMode.EXACT, "13800138000", TypeFamily.STRING),
        Rule(listOf("username", "user_name", "loginName", "login_name", "accountName", "account_name"), MatchMode.CONTAINS, "zhangsan", TypeFamily.STRING),
        Rule(listOf("nickname", "nick_name", "displayName", "display_name"), MatchMode.CONTAINS, "Test User", TypeFamily.STRING),
        Rule(listOf("realName", "real_name", "trueName", "true_name", "fullName", "full_name"), MatchMode.CONTAINS, "Zhang San", TypeFamily.STRING),
        Rule(listOf("password", "passwd"), MatchMode.CONTAINS, "123456", TypeFamily.STRING),
        Rule(listOf("pwd"), MatchMode.EXACT, "123456", TypeFamily.STRING),
        Rule(listOf("avatar", "avatarUrl", "avatar_url", "headImg", "head_img", "portrait"), MatchMode.CONTAINS, "https://example.com/avatar.png", TypeFamily.STRING),
        Rule(listOf("idCard", "id_card", "identityCard", "identity_card"), MatchMode.CONTAINS, "110101199001011234", TypeFamily.STRING),

        // ---- naming / text ----
        Rule(listOf("title"), MatchMode.EXACT, "Sample Title", TypeFamily.STRING),
        Rule(listOf("name"), MatchMode.EXACT, "test", TypeFamily.STRING),
        Rule(listOf("description"), MatchMode.CONTAINS, "This is a sample description", TypeFamily.STRING),
        Rule(listOf("desc"), MatchMode.EXACT, "This is a sample description", TypeFamily.STRING),
        Rule(listOf("content", "text", "body", "message", "msg"), MatchMode.EXACT, "Hello World", TypeFamily.STRING),
        Rule(listOf("remark", "memo"), MatchMode.CONTAINS, "备注信息", TypeFamily.STRING),
        Rule(listOf("note", "comment"), MatchMode.EXACT, "备注信息", TypeFamily.STRING),
        Rule(listOf("reason"), MatchMode.CONTAINS, "操作原因", TypeFamily.STRING),
        Rule(listOf("label", "tag"), MatchMode.EXACT, "default", TypeFamily.STRING),
        Rule(listOf("verifyCode", "verify_code", "captcha", "authCode", "auth_code"), MatchMode.CONTAINS, "1234", TypeFamily.STRING),
        Rule(listOf("code"), MatchMode.EXACT, "1234", TypeFamily.STRING),

        // ---- network / url ----
        Rule(listOf("url", "link", "href"), MatchMode.SUFFIX, "https://example.com", TypeFamily.STRING),
        Rule(listOf("callbackUrl", "callback_url", "notifyUrl", "notify_url", "webhookUrl", "webhook_url", "redirectUrl", "redirect_url", "returnUrl", "return_url"), MatchMode.CONTAINS, "https://example.com/callback", TypeFamily.STRING),
        Rule(listOf("ipAddr", "ip_addr", "ipAddress", "ip_address", "clientIp", "client_ip"), MatchMode.CONTAINS, "127.0.0.1", TypeFamily.STRING),
        Rule(listOf("ip"), MatchMode.EXACT, "127.0.0.1", TypeFamily.STRING),
        Rule(listOf("host", "domain"), MatchMode.EXACT, "example.com", TypeFamily.STRING),
        Rule(listOf("port"), MatchMode.EXACT, 8080, TypeFamily.INTEGER),

        // ---- location ----
        Rule(listOf("address"), MatchMode.CONTAINS, "北京市朝阳区", TypeFamily.STRING),
        Rule(listOf("addr"), MatchMode.EXACT, "北京市朝阳区", TypeFamily.STRING),
        Rule(listOf("city"), MatchMode.EXACT, "北京", TypeFamily.STRING),
        Rule(listOf("province", "state"), MatchMode.EXACT, "北京市", TypeFamily.STRING),
        Rule(listOf("country"), MatchMode.EXACT, "CN", TypeFamily.STRING),
        Rule(listOf("zipCode", "zip_code", "postalCode", "postal_code", "postCode", "post_code"), MatchMode.CONTAINS, "100000", TypeFamily.STRING),
        Rule(listOf("longitude"), MatchMode.CONTAINS, 116.404, TypeFamily.FLOAT),
        Rule(listOf("lng", "lat"), MatchMode.EXACT, 116.404, TypeFamily.FLOAT),
        Rule(listOf("latitude"), MatchMode.CONTAINS, 39.915, TypeFamily.FLOAT),

        // ---- numeric ids & pagination ----
        Rule(listOf("pageNum", "page_num", "pageNo", "page_no", "pageIndex", "page_index"), MatchMode.CONTAINS, 1, TypeFamily.INTEGER),
        Rule(listOf("page", "current"), MatchMode.EXACT, 1, TypeFamily.INTEGER),
        Rule(listOf("pageSize", "page_size", "perPage", "per_page"), MatchMode.CONTAINS, 10, TypeFamily.INTEGER),
        Rule(listOf("size", "limit", "rows"), MatchMode.EXACT, 10, TypeFamily.INTEGER),
        Rule(listOf("offset", "skip"), MatchMode.EXACT, 0, TypeFamily.INTEGER),
        Rule(listOf("totalCount", "total_count"), MatchMode.CONTAINS, 100, TypeFamily.INTEGER),
        Rule(listOf("total", "count"), MatchMode.EXACT, 100, TypeFamily.INTEGER),
        Rule(listOf("age"), MatchMode.EXACT, 25, TypeFamily.INTEGER),
        Rule(listOf("orderBy", "order_by", "sortBy", "sort_by"), MatchMode.CONTAINS, "id", TypeFamily.STRING),
        Rule(listOf("sort"), MatchMode.EXACT, "id", TypeFamily.STRING),
        Rule(listOf("sortOrder", "sort_order"), MatchMode.CONTAINS, "desc", TypeFamily.STRING),
        Rule(listOf("order", "direction"), MatchMode.EXACT, "desc", TypeFamily.STRING),

        // ---- money / amount ----
        // Typed so a BigDecimal/Double price is a JSON number, not the string "99.99"
        Rule(listOf("price", "amount", "money", "fee", "cost", "balance", "salary"), MatchMode.CONTAINS, 99.99, TypeFamily.FLOAT),
        // Integer amounts are usually in cents
        Rule(listOf("price", "amount", "money", "fee", "cost", "balance", "salary"), MatchMode.CONTAINS, 9999, TypeFamily.INTEGER),
        Rule(listOf("price", "amount", "money", "fee", "cost", "balance", "salary"), MatchMode.CONTAINS, 9999, TypeFamily.LONG),
        Rule(listOf("price", "amount", "money", "fee", "cost", "balance", "salary"), MatchMode.CONTAINS, "99.99", TypeFamily.STRING),
        Rule(listOf("quantity", "qty"), MatchMode.CONTAINS, 1, TypeFamily.INTEGER),
        Rule(listOf("num"), MatchMode.EXACT, 1, TypeFamily.INTEGER),
        Rule(listOf("rate", "ratio", "percent", "percentage", "discount"), MatchMode.CONTAINS, 0.5, TypeFamily.FLOAT),
        Rule(listOf("weight"), MatchMode.EXACT, 1.0, TypeFamily.FLOAT),

        // ---- status / type / enum-like strings ----
        Rule(listOf("status"), MatchMode.EXACT, 1, TypeFamily.INTEGER),
        Rule(listOf("status"), MatchMode.EXACT, "ACTIVE", TypeFamily.STRING),
        Rule(listOf("type"), MatchMode.EXACT, 1, TypeFamily.INTEGER),
        Rule(listOf("type"), MatchMode.EXACT, "DEFAULT", TypeFamily.STRING),
        Rule(listOf("enabled", "enable", "visible", "deleted", "locked"), MatchMode.CONTAINS, true, TypeFamily.BOOLEAN),
        Rule(listOf("active"), MatchMode.EXACT, true, TypeFamily.BOOLEAN),

        // ---- date / time ----
        Rule(listOf("startTime", "start_time", "beginTime", "begin_time", "startDate", "start_date", "beginDate", "begin_date", "createTime", "create_time"), MatchMode.CONTAINS, "2024-01-01 00:00:00", TypeFamily.STRING),
        Rule(listOf("endTime", "end_time", "endDate", "end_date", "expireTime", "expire_time", "deadline"), MatchMode.CONTAINS, "2024-12-31 23:59:59", TypeFamily.STRING),
        Rule(listOf("birthday", "birthDate", "birth_date"), MatchMode.CONTAINS, "1990-01-01", TypeFamily.STRING),
        Rule(listOf("birth"), MatchMode.EXACT, "1990-01-01", TypeFamily.STRING),
        Rule(listOf("year"), MatchMode.EXACT, 2024, TypeFamily.INTEGER),
        Rule(listOf("month"), MatchMode.EXACT, 1, TypeFamily.INTEGER),
        Rule(listOf("day"), MatchMode.EXACT, 1, TypeFamily.INTEGER),

        // ---- file / media ----
        Rule(listOf("fileName", "file_name", "filename"), MatchMode.CONTAINS, "example.pdf", TypeFamily.STRING),
        Rule(listOf("filePath", "file_path"), MatchMode.CONTAINS, "/data/files/example.pdf", TypeFamily.STRING),
        Rule(listOf("path"), MatchMode.EXACT, "/data/files/example.pdf", TypeFamily.STRING),
        Rule(listOf("fileSize", "file_size"), MatchMode.CONTAINS, 1024, TypeFamily.INTEGER),
        Rule(listOf("fileType", "file_type", "mimeType", "mime_type", "contentType", "content_type"), MatchMode.CONTAINS, "application/pdf", TypeFamily.STRING),
        Rule(listOf("extension"), MatchMode.CONTAINS, "pdf", TypeFamily.STRING),
        Rule(listOf("ext"), MatchMode.EXACT, "pdf", TypeFamily.STRING),
        Rule(listOf("imageUrl", "image_url", "imgUrl", "img_url"), MatchMode.CONTAINS, "https://example.com/image.png", TypeFamily.STRING),
        Rule(listOf("image", "img", "picture", "pic", "photo"), MatchMode.EXACT, "https://example.com/image.png", TypeFamily.STRING),
        Rule(listOf("videoUrl", "video_url"), MatchMode.CONTAINS, "https://example.com/video.mp4", TypeFamily.STRING),
        Rule(listOf("video"), MatchMode.EXACT, "https://example.com/video.mp4", TypeFamily.STRING),

        // ---- token / key ----
        Rule(listOf("accessToken", "access_token", "refreshToken", "refresh_token"), MatchMode.CONTAINS, "eyJhbGciOiJIUzI1NiJ9.example", TypeFamily.STRING),
        Rule(listOf("token"), MatchMode.EXACT, "eyJhbGciOiJIUzI1NiJ9.example", TypeFamily.STRING),
        Rule(listOf("appKey", "app_key", "apiKey", "api_key", "secretKey", "secret_key", "appId", "app_id"), MatchMode.CONTAINS, "your_app_key_here", TypeFamily.STRING),
        Rule(listOf("signature"), MatchMode.CONTAINS, "abc123def456", TypeFamily.STRING),
        Rule(listOf("sign"), MatchMode.EXACT, "abc123def456", TypeFamily.STRING),
        Rule(listOf("nonce"), MatchMode.EXACT, "a1b2c3d4", TypeFamily.STRING),
        Rule(listOf("timestamp"), MatchMode.CONTAINS, 1704067200000L, TypeFamily.LONG),

        // ---- generic id (keep last among id-rules) ----
        Rule(listOf("Id"), MatchMode.SUFFIX, 1, TypeFamily.INTEGER),
        Rule(listOf("Id"), MatchMode.SUFFIX, 1L, TypeFamily.LONG),
        Rule(listOf("Id"), MatchMode.SUFFIX, "1", TypeFamily.STRING),
        Rule(listOf("id"), MatchMode.EXACT, 1, TypeFamily.INTEGER),
        Rule(listOf("id"), MatchMode.EXACT, 1L, TypeFamily.LONG),
        Rule(listOf("id"), MatchMode.EXACT, "1", TypeFamily.STRING),

        // ---- version ----
        Rule(listOf("version"), MatchMode.EXACT, "1.0.0", TypeFamily.STRING),
        Rule(listOf("version"), MatchMode.EXACT, 1, TypeFamily.INTEGER),

        // ---- search / keyword ----
        Rule(listOf("keyword", "keywords", "searchKey", "search_key"), MatchMode.CONTAINS, "test", TypeFamily.STRING),
        Rule(listOf("search", "query", "q", "key"), MatchMode.EXACT, "test", TypeFamily.STRING),
    )

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    /**
     * Returns a smart sample value for a given parameter/field name and type.
     * Returns `null` if no name-based rule matches (caller should fall back to
     * the type-only default).
     */
    fun forName(name: String, typeName: String): Any? {
        val family = resolveTypeFamily(typeName)
        val lower = name.lowercase()

        for (rule in rules) {
            if (rule.typeFamily != null && rule.typeFamily != family) continue
            val matched = rule.keywords.any { kw ->
                when (rule.matchMode) {
                    MatchMode.EXACT -> lower == kw.lowercase()
                    MatchMode.CONTAINS -> lower.contains(kw.lowercase())
                    MatchMode.SUFFIX -> lower.endsWith(kw.lowercase())
                    MatchMode.PREFIX -> lower.startsWith(kw.lowercase())
                }
            }
            if (matched) return rule.sample
        }
        return null
    }

    /**
     * String version: returns the sample value as a String (for query params, headers, path vars).
     * Returns `null` if no match.
     */
    fun forNameAsString(name: String, typeName: String): String? =
        forName(name, typeName)?.toString()

    // ----------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------

    fun resolveTypeFamily(typeName: String): TypeFamily {
        val t = typeName.substringAfterLast(".").substringBefore("<")
        return when {
            t in setOf("String", "CharSequence") -> TypeFamily.STRING
            t in setOf("int", "Integer", "short", "Short", "byte", "Byte") -> TypeFamily.INTEGER
            t in setOf("long", "Long") -> TypeFamily.LONG
            t in setOf("double", "Double", "float", "Float", "BigDecimal") -> TypeFamily.FLOAT
            t in setOf("boolean", "Boolean") -> TypeFamily.BOOLEAN
            t in setOf("Date", "LocalDate") -> TypeFamily.DATE
            t in setOf("LocalDateTime", "Instant", "LocalTime") -> TypeFamily.DATETIME
            else -> TypeFamily.OTHER
        }
    }
}

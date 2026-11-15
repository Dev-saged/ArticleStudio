package com.sajed.articlestudio.schema

typealias Row = Map<String, String>

enum class FieldType { Text, Number, Secret, Toggle, Choice }

class Field(
    val key: String,
    val label: String,
    val type: FieldType = FieldType.Text,
    val required: Boolean = false,
    val options: List<String> = emptyList(),
    val min: Int? = null,
    val max: Int? = null,
)

class Kpi(val label: String, val value: (List<Row>) -> String)

/** وحدة = كائن مخطط صغير؛ المحرك العام يرسم النموذج والقائمة والمرشحات والمؤشرات منه. */
class ModuleSchema(
    val id: String,
    val title: String,
    val fields: List<Field>,
    val listColumns: List<String>,
    val filterKey: String? = null,
    val kpis: List<Kpi> = emptyList(),
)

object SchemaRules {
    fun sanitize(raw: String, max: Int = 500): String =
        raw.filter { !it.isISOControl() || it == '\n' }.trim().take(max)

    fun validate(schema: ModuleSchema, values: Row): Map<String, String> {
        val errors = LinkedHashMap<String, String>()
        for (f in schema.fields) {
            val v = values[f.key].orEmpty().trim()
            if (f.required && v.isEmpty() && f.type != FieldType.Toggle) { errors[f.key] = "مطلوب"; continue }
            if (v.isEmpty()) continue
            if (f.type == FieldType.Number) {
                val n = v.toIntOrNull()
                when {
                    n == null -> errors[f.key] = "رقم غير صالح"
                    f.min != null && n < f.min -> errors[f.key] = "الحد الأدنى ${f.min}"
                    f.max != null && n > f.max -> errors[f.key] = "الحد الأعلى ${f.max}"
                }
            }
            if (f.type == FieldType.Choice && v !in f.options) errors[f.key] = "خيار غير معروف"
        }
        return errors
    }
}

object Schemas {
    val article = ModuleSchema(
        id = "article", title = "المقالات",
        fields = listOf(
            Field("title", "العنوان", required = true),
            Field("words", "الكلمات", FieldType.Number, min = 0),
            Field("provider", "المزود"),
            Field("status", "الحالة", FieldType.Choice, options = listOf("ready", "failed", "pending")),
        ),
        listColumns = listOf("title", "words", "provider"),
        filterKey = "status",
        kpis = listOf(
            Kpi("جاهزة") { r -> r.count { it["status"] == "ready" }.toString() },
            Kpi("متوسط الكلمات") { r ->
                val w = r.mapNotNull { it["words"]?.toIntOrNull() }.filter { it > 0 }
                if (w.isEmpty()) "—" else (w.sum() / w.size).toString()
            },
        ),
    )

    val provider = ModuleSchema(
        id = "provider", title = "المزودون",
        fields = listOf(
            Field("key", "المفتاح", FieldType.Secret),
            Field("enabled", "مفعّل", FieldType.Toggle),
        ),
        listColumns = listOf("name"),
    )

    val providerCustom = ModuleSchema(
        id = "provider-custom", title = "مزود مخصص",
        fields = listOf(
            Field("base", "الرابط الأساسي (…/v1)", required = true),
            Field("model", "النموذج", required = true),
            Field("key", "المفتاح", FieldType.Secret),
            Field("enabled", "مفعّل", FieldType.Toggle),
        ),
        listColumns = listOf("name"),
    )

    val articleEdit = ModuleSchema(
        id = "article-edit", title = "تعديل المقال",
        fields = listOf(
            Field("title", "العنوان", required = true),
            Field("desc", "الوصف"),
            Field("labels", "التصنيفات (بفاصلة)"),
        ),
        listColumns = listOf("title"),
    )

    val github = ModuleSchema(
        id = "github", title = "GitHub",
        fields = listOf(
            Field("repo", "المستودع owner/name", required = true),
            Field("branch", "الفرع"),
            Field("token", "التوكن", FieldType.Secret),
            Field("sched", "جدولة النشر بالساعات (0 = فوري)", FieldType.Number, min = 0, max = 72),
            Field("auto", "رفع الصور تلقائياً", FieldType.Toggle),
        ),
        listColumns = listOf("repo"),
    )
}

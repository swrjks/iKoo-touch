package com.sudocode.ikoo.gallery_ai

import com.sudocode.ikoo.core.ai.AIEngine
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * Result of parsing a natural-language gallery search query.
 */
data class SmartSearchResult(
    val places: MutableList<KnownPlace> = mutableListOf(),
    val contentTags: MutableSet<String> = mutableSetOf(),
    var textKeywords: List<String> = emptyList(),
    var dateRange: GalleryDateRange? = null
) {
    val isEmpty: Boolean
        get() = places.isEmpty() && contentTags.isEmpty() && textKeywords.isEmpty() && dateRange == null
}

/**
 * Fully offline natural-language parser for gallery queries.
 *
 * Ported 1:1 (logic-equivalent) from the working Flutter implementation in
 * `ikoo_memories` (z.zip `lib/main.dart`): place/coordinate extraction,
 * relative + absolute date parsing, and content tag matching.
 *
 * Examples this correctly understands:
 *  - "Show me photos from Gujarat"
 *  - "photos clicked in Hyderabad last week"
 *  - "photo taken at Goa last Thursday"
 *  - "photos near 17.3850, 78.4867"
 *  - "beach photos from last month"
 *  - "temple photos in Mysore"
 */
object SmartSearchParser {

    private val CONTENT_TAGS: Map<String, List<String>> = mapOf(
        "beach" to listOf("beach", "sea", "ocean", "shore", "coast"),
        "food" to listOf("food", "meal", "dinner", "lunch", "breakfast", "restaurant", "cafe"),
        "selfie" to listOf("selfie", "self-portrait", "portrait"),
        "nature" to listOf("nature", "forest", "garden", "park", "tree"),
        "building" to listOf("building", "architecture", "tower"),
        "temple" to listOf("temple", "shrine", "palace"),
        "party" to listOf("party", "celebration", "birthday", "wedding"),
        "sunset" to listOf("sunset", "evening", "dusk"),
        "car" to listOf("car", "vehicle", "drive", "automobile"),
        "iqoo" to listOf("iqoo", "i qoo", "vivo"),
        "screenshot" to listOf("screenshot", "screen shot", "screen grab"),
        "phone" to listOf("phone", "mobile", "smartphone", "iphone", "device"),
        "backside" to listOf("backside", "back side", "back", "rear", "back cover"),
        "qr" to listOf("qr", "qr code", "barcode", "scan code"),
        "document" to listOf("document", "paper", "page", "note"),
        "scalar" to listOf("scalar", "scaler", "school of technology"),
        "logo" to listOf("logo", "sign", "signboard", "poster", "banner", "board"),
        "laptop" to listOf("laptop", "computer", "pc", "macbook", "notebook", "keyboard"),
        "bottle" to listOf("water bottle", "bottle", "drink bottle", "flask", "thermos", "water flask"),
        "hand" to listOf("hand", "palm", "finger", "fingers", "arm"),
        "table" to listOf("table", "desk", "workbench"),
        "charger" to listOf("charger", "cable", "wire", "usb", "charging cable"),
        "bag" to listOf("bag", "backpack", "pouch"),
        "book" to listOf("book", "notebook", "register"),
        "watch" to listOf("watch", "clock")
    )

    private val WEEKDAY_ALIASES: Map<Int, List<String>> = mapOf(
        Calendar.MONDAY to listOf("monday", "mon"),
        Calendar.TUESDAY to listOf("tuesday", "tue", "tues"),
        Calendar.WEDNESDAY to listOf("wednesday", "wed"),
        Calendar.THURSDAY to listOf("thursday", "thu", "thur", "thurs"),
        Calendar.FRIDAY to listOf("friday", "fri"),
        Calendar.SATURDAY to listOf("saturday", "sat"),
        Calendar.SUNDAY to listOf("sunday", "sun")
    )

    private val MONTH_ALIASES: Map<Int, List<String>> = mapOf(
        Calendar.JANUARY to listOf("january", "jan"),
        Calendar.FEBRUARY to listOf("february", "feb"),
        Calendar.MARCH to listOf("march", "mar"),
        Calendar.APRIL to listOf("april", "apr"),
        Calendar.MAY to listOf("may"),
        Calendar.JUNE to listOf("june", "jun"),
        Calendar.JULY to listOf("july", "jul"),
        Calendar.AUGUST to listOf("august", "aug"),
        Calendar.SEPTEMBER to listOf("september", "sep", "sept"),
        Calendar.OCTOBER to listOf("october", "oct"),
        Calendar.NOVEMBER to listOf("november", "nov"),
        Calendar.DECEMBER to listOf("december", "dec")
    )

    private val COORDINATE_PATTERN = Regex(
        "(-?\\d{1,2}(?:\\.\\d+)?)\\s*[, ]\\s*(-?\\d{1,3}(?:\\.\\d+)?)"
    )

    fun parse(query: String): SmartSearchResult {
        val lowerQuery = query.lowercase(Locale.US).replace(Regex("[^a-z0-9\\s]"), " ")
        val result = SmartSearchResult()

        extractCoordinatePlace(query)?.let { result.places.add(it) }

        for (place in KnownPlaceCatalog.places) {
            for (alias in place.aliases) {
                if (containsWordOrPhrase(lowerQuery, alias)) {
                    result.places.add(place)
                    break
                }
            }
        }

        result.dateRange = parseDateRange(lowerQuery)

        for ((tag, keywords) in CONTENT_TAGS) {
            for (keyword in keywords) {
                if (containsWordOrPhrase(lowerQuery, keyword)) {
                    result.contentTags.add(tag)
                    break
                }
            }
        }

        if (result.places.isEmpty() && result.dateRange == null && result.contentTags.isEmpty()) {
            result.textKeywords = lowerQuery
                .split(Regex("\\s+"))
                .filter { word ->
                    word.length > 2 &&
                        word != "photo" && word != "photos" &&
                        word != "picture" && word != "pictures" &&
                        word != "image" && word != "images" &&
                        word != "show" && word != "find" && word != "search" &&
                        word != "look" && word != "get" && word != "me"
                }
        }

        return result
    }

    /**
     * Uses Qwen to normalize noisy speech/text, then runs the reliable local
     * parser and GPS matcher. Any model or JSON failure returns [parse].
     */
    suspend fun parseWithAi(query: String, engine: AIEngine?): SmartSearchResult {
        val fallback = parse(query)
        if (engine == null || !engine.isReady()) return fallback

        val prompt = """
            Extract this photo-gallery search into JSON only. No markdown.
            Do not invent coordinates. Include coordinates only when explicitly
            present in the query or when you are highly certain.

            Schema:
            {
              "placeText": "place names exactly as understood, or empty",
              "dateText": "today/yesterday/last week/last Thursday/month, or empty",
              "coordinates": [{"latitude": 0.0, "longitude": 0.0}],
              "tags": ["beach","food","selfie","nature","building","temple","party","sunset","car","iqoo","screenshot","phone","backside","qr","document","scalar","logo","laptop"],
              "keywords": ["other useful filename words"]
            }

            Query: "$query"
        """.trimIndent()

        val response = engine.generate(prompt, 384) ?: return fallback
        val jsonText = extractJsonObject(response) ?: return fallback

        return runCatching {
            val json = JSONObject(jsonText)
            val placeText = json.optString("placeText")
            val dateText = json.optString("dateText")
            val tags = json.optJSONArray("tags")
            val normalizedQuery = buildString {
                append(query)
                if (placeText.isNotBlank()) append(' ').append(placeText)
                if (dateText.isNotBlank()) append(' ').append(dateText)
                if (tags != null) {
                    for (index in 0 until tags.length()) {
                        append(' ').append(tags.optString(index))
                    }
                }
            }

            val result = parse(normalizedQuery)
            json.optJSONArray("coordinates")?.let { coordinates ->
                for (index in 0 until coordinates.length()) {
                    val coordinate = coordinates.optJSONObject(index) ?: continue
                    val latitude = coordinate.optDouble("latitude", Double.NaN)
                    val longitude = coordinate.optDouble("longitude", Double.NaN)
                    if (
                        latitude.isFinite() && longitude.isFinite() &&
                        latitude in -90.0..90.0 && longitude in -180.0..180.0
                    ) {
                        result.places.add(
                            KnownPlace(
                                canonicalName = "qwen coordinates",
                                latitude = latitude,
                                longitude = longitude,
                                aliases = listOf("qwen coordinates")
                            )
                        )
                    }
                }
            }

            if (result.textKeywords.isEmpty()) {
                val keywords = json.optJSONArray("keywords")
                if (keywords != null) {
                    result.textKeywords = buildList {
                        for (index in 0 until keywords.length()) {
                            val word = keywords.optString(index).lowercase(Locale.US).trim()
                            if (word.length > 2) add(word)
                        }
                    }
                }
            }

            if (result.isEmpty) fallback else result
        }.getOrDefault(fallback)
    }

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun containsWordOrPhrase(query: String, value: String): Boolean {
        val escaped = Regex.escape(value.lowercase(Locale.US))
        return Regex("(^|\\s)$escaped(\\s|\$)").containsMatchIn(query)
    }

    private fun extractCoordinatePlace(query: String): KnownPlace? {
        val match = COORDINATE_PATTERN.find(query) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null
        return KnownPlace("custom coordinates", lat, lon, listOf("coordinates"))
    }

    private fun parseDateRange(query: String): GalleryDateRange? {
        val today = startOfToday()

        if (containsWordOrPhrase(query, "today")) return GalleryDateRange.day(today)
        if (containsWordOrPhrase(query, "yesterday")) {
            return GalleryDateRange.day(addDays(today, -1))
        }
        if (containsWordOrPhrase(query, "last week") || containsWordOrPhrase(query, "past week")) {
            return GalleryDateRange(addDays(today, -7), addDays(today, 1))
        }
        if (containsWordOrPhrase(query, "last month") || containsWordOrPhrase(query, "past month")) {
            return GalleryDateRange(addDays(today, -30), addDays(today, 1))
        }
        if (containsWordOrPhrase(query, "last year") || containsWordOrPhrase(query, "past year")) {
            return GalleryDateRange(addDays(today, -365), addDays(today, 1))
        }

        for ((dayOfWeek, aliases) in WEEKDAY_ALIASES) {
            val mentionsDay = aliases.any { containsWordOrPhrase(query, it) }
            if (!mentionsDay) continue

            val cal = Calendar.getInstance()
            cal.time = today
            val todayDow = cal.get(Calendar.DAY_OF_WEEK)
            var daysBack = (todayDow - dayOfWeek + 7) % 7
            val wantsLast = containsWordOrPhrase(query, "last") || containsWordOrPhrase(query, "previous")
            if (daysBack == 0 || wantsLast) {
                daysBack = if (daysBack == 0) 7 else daysBack
            }
            return GalleryDateRange.day(addDays(today, -daysBack))
        }

        for ((month, aliases) in MONTH_ALIASES) {
            if (aliases.any { containsWordOrPhrase(query, it) }) {
                val cal = Calendar.getInstance()
                cal.time = today
                var year = cal.get(Calendar.YEAR)
                val currentMonth = cal.get(Calendar.MONTH)
                if (month > currentMonth) year -= 1

                cal.clear()
                cal.set(year, month, 1, 0, 0, 0)
                val start = cal.time
                cal.add(Calendar.MONTH, 1)
                val end = cal.time
                return GalleryDateRange(start, end)
            }
        }

        return null
    }

    private fun startOfToday(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }

    private fun addDays(date: Date, days: Int): Date {
        val cal = Calendar.getInstance()
        cal.time = date
        cal.add(Calendar.DAY_OF_MONTH, days)
        return cal.time
    }
}

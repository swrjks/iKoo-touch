package com.sudocode.ikoo.gallery_ai

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sudocode.ikoo.core.vision.VisionEngine
import com.sudocode.ikoo.core.vision.VisionEngineRegistry
import com.sudocode.ikoo.core.vision.VisionInput
import com.sudocode.ikoo.core.vision.VisionResult
import com.sudocode.ikoo.core.vision.searchTokens
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single gallery photo with metadata pulled from [MediaStore] and, when
 * available, GPS coordinates read from the file's EXIF data.
 */
data class GalleryPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTaken: Date,
    val latitude: Double?,
    val longitude: Double?,
    val relativePath: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val visionResult: VisionResult = VisionResult(engineName = "none")
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val isScreenshot: Boolean
        get() {
            val source = "$displayName $relativePath".lowercase(Locale.US)
            return source.contains("screenshot") || source.contains("screen shot") ||
                source.contains("screenshots")
        }

    val orientationLabel: String
        get() = when {
            width <= 0 || height <= 0 -> "photo"
            width == height -> "square"
            width > height -> "landscape"
            else -> "portrait"
        }

    val inferredPlace: KnownPlace?
        get() {
            val name = "$displayName $relativePath".lowercase(Locale.US)
            KnownPlaceCatalog.places.firstOrNull { place ->
                place.aliases.any { alias -> name.contains(alias) }
            }?.let { return it }

            if (!hasLocation) return null
            return KnownPlaceCatalog.places.minByOrNull { place: KnownPlace ->
                galleryDistanceKm(place.latitude, place.longitude, latitude!!, longitude!!)
            }?.takeIf { place ->
                place.isNear(latitude!!, longitude!!)
            }
        }

    fun formattedDate(): String =
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(dateTaken)

    fun semanticText(): String = buildString {
        append(displayName.lowercase(Locale.US).replace(Regex("[._-]"), " "))
        append(' ').append(relativePath.lowercase(Locale.US).replace('/', ' '))
        append(' ').append(orientationLabel)
        append(' ').append(mimeType.lowercase(Locale.US))
        visionResult.searchableTokens().forEach { append(' ').append(it) }
        if (isScreenshot) append(" screenshot screen app ui")
        inferredPlace?.let { place ->
            append(' ').append(place.canonicalName)
            place.aliases.forEach { append(' ').append(it) }
        }
        append(' ').append(SimpleDateFormat("MMMM yyyy EEEE", Locale.US).format(dateTaken).lowercase(Locale.US))
    }
}

data class GallerySearchReport(
    val search: SmartSearchResult,
    val matches: List<GalleryPhoto>,
    val summary: String,
    val qwenUsed: Boolean
)

/**
 * Loads photos from the device's [MediaStore] and reads EXIF GPS metadata
 * for location-based search. Fully functional against real device storage
 * (requires READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE at call time).
 */
object GalleryRepository {

    /**
     * Loads up to [limit] most-recent images, including EXIF GPS where
     * present. This performs file I/O for EXIF reads and should be called
     * from a background dispatcher.
     */
    fun loadPhotos(context: Context, limit: Int = 500): List<GalleryPhoto> {
        val photos = mutableListOf<GalleryPhoto>()

        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val dateTakenMillis = cursor.getLong(dateTakenCol)
                val dateAddedSeconds = cursor.getLong(dateAddedCol)
                val relativePath = cursor.getString(pathCol).orEmpty()
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)
                val mimeType = cursor.getString(mimeCol).orEmpty()
                val date = if (dateTakenMillis > 0) {
                    Date(dateTakenMillis)
                } else {
                    Date(dateAddedSeconds * 1000L)
                }

                val uri = ContentUris.withAppendedId(collection, id)
                val (lat, lon) = readExifLocation(context, uri)
                val visionResult = (VisionEngineRegistry.active() ?: MlKitVisionEngine).describeImage(
                    context = context,
                    input = VisionInput(
                        uri = uri,
                        displayName = name,
                        relativePath = relativePath,
                        width = width,
                        height = height
                    )
                )

                photos.add(
                    GalleryPhoto(
                        id = id,
                        uri = uri,
                        displayName = name,
                        dateTaken = date,
                        latitude = lat,
                        longitude = lon,
                        relativePath = relativePath,
                        width = width,
                        height = height,
                        mimeType = mimeType,
                        visionResult = visionResult
                    )
                )
                count++
            }
        }

        return photos
    }

    private fun readExifLocation(context: Context, uri: Uri): Pair<Double?, Double?> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    latLong[0].toDouble() to latLong[1].toDouble()
                } else {
                    null to null
                }
            } ?: (null to null)
        } catch (e: IOException) {
            null to null
        } catch (e: SecurityException) {
            null to null
        }
    }

    /**
     * Filters [photos] using a parsed [SmartSearchResult]. Mirrors the
     * filtering logic from the Flutter prototype: place (GPS or filename
     * match), date range, content tags (filename heuristic), and free-text
     * keywords (filename match).
     */
    fun filter(photos: List<GalleryPhoto>, search: SmartSearchResult): List<GalleryPhoto> {
        if (search.isEmpty) return photos

        return photos.filter { photo ->
            val semanticText = photo.semanticText()
            if (search.places.isNotEmpty()) {
                var locationMatch = false
                if (photo.hasLocation) {
                    locationMatch = search.places.any { place ->
                        place.isNear(photo.latitude!!, photo.longitude!!)
                    }
                }
                if (!locationMatch) {
                    locationMatch = search.places.any { place ->
                        place.aliases.any { alias -> semanticText.contains(alias) }
                    }
                }
                if (!locationMatch) return@filter false
            }

            val range = search.dateRange
            if (range != null && !range.contains(photo.dateTaken)) {
                return@filter false
            }

            if (search.contentTags.isNotEmpty()) {
                val tagMatch = search.contentTags.all { tag ->
                    tagMatchesSemanticText(tag, semanticText)
                }
                if (!tagMatch) return@filter false
            }

            if (search.textKeywords.isNotEmpty()) {
                val keywordMatch = search.textKeywords.any { keyword -> semanticText.contains(keyword) }
                if (!keywordMatch) return@filter false
            }

            true
        }.sortedByDescending { photo ->
            relevanceScore(photo.semanticText(), search)
        }
    }

    private fun relevanceScore(semanticText: String, search: SmartSearchResult): Int {
        var score = 0
        search.contentTags.forEach { tag ->
            if (semanticText.contains(tag)) score += 10
            if (tag == "laptop" && listOf("computer", "keyboard", "notebook", "macbook").any { semanticText.contains(it) }) {
                score += 7
            }
            if (tag == "bottle" && listOf("water bottle", "drink bottle", "flask", "thermos").any { semanticText.contains(it) }) {
                score += 7
            }
            if (tag == "scalar" && semanticText.contains("scaler")) score += 9
        }
        search.textKeywords.forEach { keyword ->
            if (semanticText.contains(keyword)) score += 4
        }
        return score
    }

    private fun tagMatchesSemanticText(tag: String, semanticText: String): Boolean {
        if (semanticText.contains(tag)) return true
        val synonyms = when (tag) {
            "laptop" -> listOf("computer", "notebook", "keyboard", "macbook")
            "phone" -> listOf("smartphone", "mobile", "iphone", "device")
            "bottle" -> listOf("water bottle", "drink bottle", "flask", "thermos", "beverage bottle")
            "hand" -> listOf("palm", "finger", "fingers", "arm")
            "charger" -> listOf("cable", "wire", "usb", "charging cable")
            "table" -> listOf("desk", "workbench")
            "qr" -> listOf("qr code", "barcode", "scan", "code")
            "logo" -> listOf("sign", "signboard", "poster", "banner", "board")
            "scalar" -> listOf("scaler")
            else -> emptyList()
        }
        return synonyms.any { semanticText.contains(it) }
    }

    fun explainSearch(search: SmartSearchResult, matches: List<GalleryPhoto>, qwenUsed: Boolean): String {
        val parts = mutableListOf<String>()
        if (qwenUsed) parts += "On-device AI refined the request"
        if (search.places.isNotEmpty()) {
            parts += "place: ${search.places.distinctBy { it.canonicalName }.joinToString { it.canonicalName }}"
        }
        search.dateRange?.let { parts += "date filtered" }
        if (search.contentTags.isNotEmpty()) parts += "visual cue: ${search.contentTags.joinToString()}"
        if (search.textKeywords.isNotEmpty()) parts += "keywords: ${search.textKeywords.joinToString()}"
        if (parts.isEmpty()) parts += "showing recent photos"
        return "${matches.size} result${if (matches.size == 1) "" else "s"} - ${parts.joinToString(" / ")}"
    }

}

/**
 * Lightweight local image understanding for gallery search.
 *
 * This intentionally avoids network calls. Qwen normalizes the user's
 * language, while this indexer adds pixel-derived tags so queries like
 * "iPhone on backside", "QR code", "document", and "dark screenshot" can
 * match photos whose filenames contain none of those words.
 */
object MlKitVisionEngine : VisionEngine {
    override val name: String = "ML Kit offline vision"

    override fun describeImage(
        context: Context,
        input: VisionInput
    ): VisionResult {
        val text = "${input.displayName} ${input.relativePath}".lowercase(Locale.US)
        val tags = linkedSetOf<String>()

        if (text.contains("screenshot") || text.contains("screen shot") || text.contains("screenshots")) {
            tags += listOf("screenshot", "screen", "app", "ui")
        }
        if (text.contains("whatsapp")) tags += listOf("whatsapp", "chat", "message")
        if (text.contains("camera")) tags += "camera"
        if (input.width > 0 && input.height > 0) {
            tags += if (input.width > input.height) "landscape" else if (input.height > input.width) "portrait" else "square"
        }

        val bitmap = loadBitmapForVision(context, input.uri) ?: return VisionResult(name, tags = tags)
        val mlKitResult = inferMlKitResult(bitmap)
        tags += mlKitResult.tags
        val metrics = bitmap.analyzePixels()

        val likelyQrCode = metrics.blackWhiteRatio > 0.24f &&
            (metrics.transitionScore > 0.055f || metrics.gridEdgeScore > 0.075f) &&
            metrics.whiteRatio > 0.05f &&
            metrics.darkRatio > 0.08f
        if (likelyQrCode) {
            tags += listOf("qr", "qr code", "barcode", "code", "scan")
        }
        if (metrics.whiteRatio > 0.48f && metrics.darkRatio > 0.04f) {
            tags += listOf("document", "paper", "page", "note")
        }
        val likelyBlueSign = metrics.blueRatio > 0.10f &&
            metrics.whiteRatio > 0.08f &&
            metrics.darkRatio > 0.08f &&
            (metrics.purpleRatio > 0.025f || metrics.gridEdgeScore > 0.035f)
        if (likelyBlueSign) {
            tags += listOf(
                "sign",
                "signboard",
                "board",
                "poster",
                "logo",
                "banner",
                "school",
                "technology"
            )
        }
        if (metrics.greenRatio > 0.18f) tags += listOf("nature", "tree", "garden", "park")
        if (metrics.blueRatio > 0.22f) tags += listOf("sky", "water", "beach", "sea")
        if (metrics.warmRatio > 0.24f) tags += listOf("food", "sunset", "warm")
        if (metrics.darkRatio > 0.62f) tags += listOf("dark", "night")

        val likelyPhoneBack = !tags.contains("screenshot") &&
            metrics.neutralMetalRatio > 0.12f &&
            metrics.darkRatio in 0.04f..0.42f &&
            metrics.whiteRatio in 0.08f..0.72f
        if (likelyPhoneBack) {
            tags += listOf(
                "phone",
                "smartphone",
                "mobile",
                "iphone",
                "back",
                "backside",
                "rear",
                "camera",
                "device"
            )
        }

        return mlKitResult.copy(
            engineName = name,
            tags = tags
        )
    }

    private fun inferMlKitResult(bitmap: Bitmap): VisionResult {
        val tags = linkedSetOf<String>()
        val barcodes = mutableListOf<String>()
        val labels = mutableListOf<String>()
        var extractedText = ""
        val image = InputImage.fromBitmap(bitmap, 0)

        runCatching {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93
                )
                .enableAllPotentialBarcodes()
                .build()
            val scanner = BarcodeScanning.getClient(options)
            try {
                val detectedBarcodes = Tasks.await(scanner.process(image))
                if (detectedBarcodes.isNotEmpty()) {
                    tags += listOf("qr", "qr code", "barcode", "code", "scan")
                    detectedBarcodes.mapNotNull { it.rawValue }
                        .also { rawValues -> barcodes.addAll(rawValues) }
                        .flatMap { it.searchTokens() }
                        .forEach { tags += it }
                }
            } finally {
                scanner.close()
            }
        }

        runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val recognized = Tasks.await(recognizer.process(image))
                extractedText = recognized.text
                recognized.text.searchTokens().forEach { token ->
                    tags += token
                    tags += normalizedObjectTags(token)
                    when (token) {
                        "scaler", "scalar" -> tags += listOf("scalar", "scaler", "school", "technology", "logo", "sign")
                        "technology" -> tags += listOf("school", "poster", "sign")
                        "iphone" -> tags += listOf("phone", "smartphone", "mobile", "device")
                        "qr" -> tags += listOf("qr code", "barcode", "scan")
                    }
                }
            } finally {
                recognizer.close()
            }
        }

        runCatching {
            val labeler = ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.35f)
                    .build()
            )
            try {
                val detectedLabels = Tasks.await(labeler.process(image))
                detectedLabels.forEach { label ->
                    val text = label.text.lowercase(Locale.US)
                    labels.add(label.text)
                    tags += text
                    tags += normalizedObjectTags(text)
                    when {
                        text.contains("phone") || text.contains("mobile") -> {
                            tags += listOf("phone", "smartphone", "mobile", "iphone", "device")
                        }
                        text.contains("computer") || text.contains("laptop") ||
                            text.contains("keyboard") || text.contains("notebook") ||
                            text.contains("macbook") -> {
                            tags += listOf(
                                "laptop",
                                "computer",
                                "notebook",
                                "keyboard",
                                "desk"
                            )
                        }
                        text.contains("display") || text.contains("monitor") ||
                            text.contains("screen") || text.contains("electronic") -> {
                            tags += listOf("screen", "display", "monitor", "electronics")
                        }
                        text.contains("poster") || text.contains("sign") || text.contains("logo") -> {
                            tags += listOf("poster", "sign", "signboard", "logo", "board", "banner")
                        }
                        text.contains("technology") || text.contains("gadget") -> {
                            tags += listOf("technology", "device")
                        }
                        text.contains("document") || text.contains("paper") -> {
                            tags += listOf("document", "paper", "page", "note")
                        }
                    }
                }
            } finally {
                labeler.close()
            }
        }

        return VisionResult(
            engineName = name,
            tags = tags,
            extractedText = extractedText,
            barcodes = barcodes,
            labels = labels
        )
    }

    private fun normalizedObjectTags(text: String): Set<String> {
        return buildSet {
            when {
                text.contains("bottle") || text.contains("flask") ||
                    text.contains("thermos") || text.contains("drink") ||
                    text.contains("beverage") -> {
                    addAll(listOf("bottle", "water bottle", "flask", "thermos", "drink bottle"))
                }
                text.contains("hand") || text.contains("finger") || text.contains("palm") -> {
                    addAll(listOf("hand", "palm", "finger", "fingers", "arm"))
                }
                text.contains("cable") || text.contains("wire") ||
                    text.contains("charger") || text.contains("usb") -> {
                    addAll(listOf("charger", "cable", "wire", "usb", "charging cable"))
                }
                text.contains("desk") || text.contains("table") -> {
                    addAll(listOf("table", "desk", "workbench"))
                }
                text.contains("book") || text.contains("notebook") || text.contains("register") -> {
                    addAll(listOf("book", "notebook", "register"))
                }
                text.contains("bag") || text.contains("backpack") || text.contains("pouch") -> {
                    addAll(listOf("bag", "backpack", "pouch"))
                }
                text.contains("watch") || text.contains("clock") -> {
                    addAll(listOf("watch", "clock"))
                }
            }
        }
    }

    private fun loadThumbnail(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(144, 144), null)
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { source ->
                        Bitmap.createScaledBitmap(source, 144, 144, true)
                    }
                }
            }
        }.getOrNull()
    }

    private fun loadBitmapForVision(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { source ->
                    val maxSide = maxOf(source.width, source.height)
                    if (maxSide <= 1024) {
                        source
                    } else {
                        val scale = 1024f / maxSide
                        Bitmap.createScaledBitmap(
                            source,
                            (source.width * scale).toInt().coerceAtLeast(1),
                            (source.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                    }
                }
            } ?: loadThumbnail(context, uri)
        }.getOrNull()
    }

    private fun Bitmap.analyzePixels(): VisionMetrics {
        val stepX = maxOf(1, width / 48)
        val stepY = maxOf(1, height / 48)
        var total = 0
        var dark = 0
        var white = 0
        var neutralMetal = 0
        var green = 0
        var blue = 0
        var purple = 0
        var warm = 0
        var highContrast = 0
        var transitions = 0
        var blackWhite = 0
        var gridEdges = 0
        var previousColumnLuma: Int? = null
        var previousLuma: Int? = null

        var y = 0
        while (y < height) {
            var x = 0
            previousLuma = null
            previousColumnLuma = null
            while (x < width) {
                val color = getPixel(x, y)
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val luma = ((r * 299) + (g * 587) + (b * 114)) / 1000
                val saturation = max - min

                total++
                if (luma < 62) dark++
                if (luma > 205 && saturation < 42) white++
                if (luma in 76..206 && saturation < 34) neutralMetal++
                if (g > r + 24 && g > b + 18) green++
                if (b > r + 24 && b > g + 6) blue++
                if (b > g + 16 && r > g + 10 && saturation > 34) purple++
                if (r > b + 30 && g > b + 10) warm++
                if (luma < 64 || luma > 210) highContrast++
                if ((luma < 76 || luma > 190) && saturation < 72) blackWhite++
                previousLuma?.let { previous ->
                    if (kotlin.math.abs(previous - luma) > 86) transitions++
                }
                previousColumnLuma?.let { previous ->
                    if (kotlin.math.abs(previous - luma) > 96) gridEdges++
                }
                previousColumnLuma = luma
                previousLuma = luma
                x += stepX
            }
            y += stepY
        }

        if (total == 0) return VisionMetrics()
        return VisionMetrics(
            darkRatio = dark / total.toFloat(),
            whiteRatio = white / total.toFloat(),
            neutralMetalRatio = neutralMetal / total.toFloat(),
            greenRatio = green / total.toFloat(),
            blueRatio = blue / total.toFloat(),
            purpleRatio = purple / total.toFloat(),
            warmRatio = warm / total.toFloat(),
            highContrastRatio = highContrast / total.toFloat(),
            blackWhiteRatio = blackWhite / total.toFloat(),
            transitionScore = transitions / total.toFloat(),
            gridEdgeScore = gridEdges / total.toFloat()
        )
    }
}

private data class VisionMetrics(
    val darkRatio: Float = 0f,
    val whiteRatio: Float = 0f,
    val neutralMetalRatio: Float = 0f,
    val greenRatio: Float = 0f,
    val blueRatio: Float = 0f,
    val purpleRatio: Float = 0f,
    val warmRatio: Float = 0f,
    val highContrastRatio: Float = 0f,
    val blackWhiteRatio: Float = 0f,
    val transitionScore: Float = 0f,
    val gridEdgeScore: Float = 0f
)

private fun galleryDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
        kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    return earthRadiusKm * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

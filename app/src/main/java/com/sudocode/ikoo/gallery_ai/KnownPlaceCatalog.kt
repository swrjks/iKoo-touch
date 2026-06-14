package com.sudocode.ikoo.gallery_ai

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A named place with coordinates and search aliases. Ported 1:1 from the
 * working Flutter implementation (`ikoo_memories` / z.zip lib/main.dart).
 */
data class KnownPlace(
    val canonicalName: String,
    val latitude: Double,
    val longitude: Double,
    val aliases: List<String>,
    val radiusKm: Double = 35.0
) {
    fun isNear(photoLat: Double, photoLon: Double): Boolean {
        return distanceKm(latitude, longitude, photoLat, photoLon) <= radiusKm
    }

    companion object {
        private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val earthRadiusKm = 6371.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            return earthRadiusKm * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}

/**
 * Offline gazetteer of places, ported from the Flutter prototype. Covers
 * major Indian cities/landmarks plus a handful of international cities.
 */
object KnownPlaceCatalog {
    val places: List<KnownPlace> = listOf(
        KnownPlace("hyderabad", 17.3850, 78.4867, listOf("hyderabad", "hyd", "secunderabad")),
        KnownPlace("bangalore", 12.9716, 77.5946, listOf("bangalore", "bengaluru", "blr")),
        KnownPlace("hoskote", 13.0707, 77.7981, listOf("hoskote", "hosakote")),
        KnownPlace("chennai", 13.0827, 80.2707, listOf("chennai", "madras")),
        KnownPlace("mumbai", 19.0760, 72.8777, listOf("mumbai", "bombay")),
        KnownPlace("delhi", 28.6139, 77.2090, listOf("delhi", "ncr", "new delhi")),
        KnownPlace("kolkata", 22.5726, 88.3639, listOf("kolkata", "calcutta")),
        KnownPlace("pune", 18.5204, 73.8567, listOf("pune")),
        KnownPlace("ahmedabad", 23.0225, 72.5714, listOf("ahmedabad", "amdavad")),
        KnownPlace("surat", 21.1702, 72.8311, listOf("surat")),
        KnownPlace("jaipur", 26.9124, 75.7873, listOf("jaipur")),
        KnownPlace("lucknow", 26.8467, 80.9462, listOf("lucknow")),
        KnownPlace("kanpur", 26.4499, 80.3319, listOf("kanpur")),
        KnownPlace("nagpur", 21.1458, 79.0882, listOf("nagpur")),
        KnownPlace("indore", 22.7196, 75.8577, listOf("indore")),
        KnownPlace("bhopal", 23.2599, 77.4126, listOf("bhopal")),
        KnownPlace("patna", 25.5941, 85.1376, listOf("patna")),
        KnownPlace("kochi", 9.9312, 76.2673, listOf("kochi", "cochin")),
        KnownPlace("trivandrum", 8.5241, 76.9366, listOf("trivandrum", "thiruvananthapuram")),
        KnownPlace("goa", 15.2993, 74.1240, listOf("goa", "panaji", "calangute", "baga")),
        KnownPlace("kurnool", 15.8281, 78.0373, listOf("kurnool", "knl")),
        KnownPlace("tirupati", 13.6288, 79.4192, listOf("tirupati")),
        KnownPlace("vijayawada", 16.5062, 80.6480, listOf("vijayawada")),
        KnownPlace("visakhapatnam", 17.6868, 83.2185, listOf("vizag", "visakhapatnam")),
        KnownPlace("warangal", 17.9689, 79.5941, listOf("warangal")),
        KnownPlace("mysore", 12.2958, 76.6394, listOf("mysore", "mysuru", "mysore palace")),
        KnownPlace("mangalore", 12.9141, 74.8560, listOf("mangalore", "mangaluru")),
        KnownPlace("ooty", 11.4102, 76.6950, listOf("ooty", "udhagamandalam")),
        KnownPlace("coimbatore", 11.0168, 76.9558, listOf("coimbatore")),
        KnownPlace("madurai", 9.9252, 78.1198, listOf("madurai")),
        KnownPlace("pondicherry", 11.9416, 79.8083, listOf("pondicherry", "puducherry")),
        KnownPlace("gujarat", 22.2587, 71.1924, listOf("gujarat"), radiusKm = 250.0),
        KnownPlace("new york", 40.7128, -74.0060, listOf("new york", "nyc")),
        KnownPlace("london", 51.5072, -0.1276, listOf("london")),
        KnownPlace("paris", 48.8566, 2.3522, listOf("paris")),
        KnownPlace("dubai", 25.2048, 55.2708, listOf("dubai")),
        KnownPlace("singapore", 1.3521, 103.8198, listOf("singapore"))
    )
}

package dev.joyfin.data

import kotlinx.serialization.json.Json

/** Shared format for Retrofit and local stores; generated serializers own all field mapping. */
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

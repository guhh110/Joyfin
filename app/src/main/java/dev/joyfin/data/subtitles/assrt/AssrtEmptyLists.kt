package dev.joyfin.data.subtitles.assrt

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

/** ASSRT uses {} for empty collections. Preserve strict decoding of populated data. */
internal open class AssrtEmptyListSerializer<T>(item: KSerializer<T>) :
    JsonTransformingSerializer<List<T>>(ListSerializer(item)) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when {
        element == JsonNull -> JsonArray(emptyList())
        element is JsonObject && element.isEmpty() -> JsonArray(emptyList())
        else -> element
    }
}

internal object AssrtResultsSerializer : AssrtEmptyListSerializer<AssrtSubtitle>(AssrtSubtitle.serializer())
internal object AssrtFilesSerializer : AssrtEmptyListSerializer<AssrtFile>(AssrtFile.serializer())

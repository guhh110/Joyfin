package dev.joyfin.ui.interaction

/** In-process memory for the last episode focused on each series page. */
internal object SeriesFocusMemory {
    private val targets = mutableMapOf<String, String>()

    fun get(seriesId: String): String? = targets[seriesId]

    fun put(seriesId: String, key: String) {
        if (seriesId.isNotBlank() && key.startsWith("series:preview:")) targets[seriesId] = key
    }
}

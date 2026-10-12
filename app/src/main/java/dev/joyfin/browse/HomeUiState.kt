package dev.joyfin.browse

import dev.joyfin.data.Home

/** Home regions keep their own loading states inside content. */
data class HomeUiState(
    val content: Home? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

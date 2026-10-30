package dev.joyfin.browse

import dev.joyfin.data.Home

/** Home regions keep their own loading states inside content. */
data class HomeUiState(
    val content: Home? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val resumeAction: ResumeActionState = ResumeActionState(),
)

data class ResumeActionState(
    val itemId: String? = null,
    val markPlayed: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val completed: Boolean = false,
)

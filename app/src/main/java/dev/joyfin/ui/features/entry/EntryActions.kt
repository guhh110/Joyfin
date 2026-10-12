package dev.joyfin.ui.features.entry

/** Entry pages only receive authentication commands, never media or playback actions. */
data class EntryActions(
    val connect: (String) -> Unit,
    val login: (String, String) -> Unit,
    val changeServer: () -> Unit,
    val cancel: () -> Unit,
    val retry: () -> Unit,
)

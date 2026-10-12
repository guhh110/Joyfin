package dev.joyfin.app

/** Account information lives in EntryState; only settings requests belong here. */
data class SettingsUiState(val busy: Boolean = false, val error: String? = null)

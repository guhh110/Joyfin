package dev.joyfin.browse

/** Data survives refresh; a failed first read never masquerades as a successful empty result. */
sealed interface SeriesResource<out T> {
    data object Idle : SeriesResource<Nothing>

    data object Loading : SeriesResource<Nothing>

    data class Failed(val message: String) : SeriesResource<Nothing>

    data class Ready<T>(val value: T, val refresh: Refresh = Refresh.Idle) : SeriesResource<T>

    sealed interface Refresh {
        data object Idle : Refresh

        data object Running : Refresh

        data class Failed(val message: String) : Refresh
    }
}

val <T> SeriesResource<T>.data: T?
    get() = (this as? SeriesResource.Ready)?.value

val SeriesResource<*>.loading: Boolean
    get() =
        this is SeriesResource.Loading ||
            (this as? SeriesResource.Ready)?.refresh == SeriesResource.Refresh.Running

val SeriesResource<*>.error: String?
    get() =
        when (this) {
            is SeriesResource.Failed -> message
            is SeriesResource.Ready -> (refresh as? SeriesResource.Refresh.Failed)?.message
            else -> null
        }

fun <T> SeriesResource<T>.refreshing(): SeriesResource<T> =
    if (this is SeriesResource.Ready) copy(refresh = SeriesResource.Refresh.Running)
    else SeriesResource.Loading

fun <T> SeriesResource<T>.failed(message: String): SeriesResource<T> =
    if (this is SeriesResource.Ready) copy(refresh = SeriesResource.Refresh.Failed(message))
    else SeriesResource.Failed(message)

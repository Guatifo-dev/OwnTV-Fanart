package tv.own.owntv.glcn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * glcn: the playlist chosen in the Live TV category sheets. It narrows Live TV only; the app-wide
 * playlist (top bar) is untouched, so Home, Movies and Series keep their own scope. Not persisted:
 * a restart goes back to every Live playlist in scope.
 */
object LiveListFilter {
    private val _id = MutableStateFlow(-1L)
    val id: StateFlow<Long> = _id

    fun set(id: Long) { _id.value = if (id > 0L) id else -1L }
}

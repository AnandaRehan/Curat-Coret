package com.ehan.curatcoret

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.ehan.curatcoret.data.DrawingData
import com.ehan.curatcoret.data.DrawingRepository
import com.ehan.curatcoret.data.HistoryEntry
import com.ehan.curatcoret.data.InkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ToolMode { DRAW, PAN }

/**
 * Drawing state + history.
 *
 * - Every change is auto-saved (debounced) under the current drawing id.
 * - The id lives in [SavedStateHandle]: it survives rotation and process death while the app
 *   is still "open", but is gone once the user really leaves the app. A fresh launch therefore
 *   always starts a new blank drawing, and the previous one stays in history under its id.
 * - Drawings with zero strokes are never kept in history.
 */
class CanvasViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val repository = DrawingRepository(File(application.filesDir, "drawings"))
    private val ioLock = Mutex()
    private val saveRequests = Channel<Unit>(Channel.CONFLATED)

    var mode by mutableStateOf(ToolMode.DRAW)
        private set

    /** Zoom factor (1f = 100%). */
    var scale by mutableFloatStateOf(1f)
        private set

    /** Pan offset in screen pixels. */
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Canvas size in pixels, used as the zoom pivot (screen center). */
    var canvasSize: Size = Size.Zero

    /** Finished strokes, stored in world coordinates. */
    val strokes = mutableStateListOf<InkStroke>()

    /** Strokes in progress, one per finger (multi-touch). */
    val activeStrokes = mutableStateMapOf<Long, SnapshotStateList<Offset>>()

    var currentDrawingId by mutableStateOf("")
        private set

    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    private val lastPointerPos = HashMap<Long, Offset>()

    /** False while a drawing is being loaded, so we never overwrite it with an empty canvas. */
    private var ready = false

    init {
        val restoredId = savedState.get<String>(KEY_DRAWING_ID)
        if (restoredId == null) {
            startBlank()
            ready = true
        } else {
            currentDrawingId = restoredId
            viewModelScope.launch {
                loadFromDisk(restoredId)?.let(::apply)
                ready = true
            }
        }
        viewModelScope.launch {
            saveRequests.receiveAsFlow().collectLatest {
                delay(SAVE_DELAY_MS)
                persist()
            }
        }
    }

    // ---- tools / view ---------------------------------------------------------------------

    fun selectMode(newMode: ToolMode) {
        mode = newMode
    }

    /** Screen pixels -> world coordinates (1 world unit = 1dp at 100% zoom). */
    private fun worldPos(screen: Offset, density: Float): Offset =
        (screen - offset) / (scale * density)

    fun onPointerDown(id: Long, pos: Offset, density: Float) {
        lastPointerPos[id] = pos
        if (mode == ToolMode.DRAW) {
            activeStrokes[id] = mutableStateListOf(worldPos(pos, density))
        }
    }

    fun onPointerMove(id: Long, pos: Offset, density: Float) {
        val last = lastPointerPos[id] ?: return
        when (mode) {
            ToolMode.DRAW -> activeStrokes[id]?.add(worldPos(pos, density))
            ToolMode.PAN -> {
                offset += pos - last
                markDirty()
            }
        }
        lastPointerPos[id] = pos
    }

    fun onPointerUp(id: Long) {
        activeStrokes.remove(id)?.let { points ->
            if (points.size >= 2) {
                strokes.add(InkStroke.fromPoints(points))
                markDirty()
            }
        }
        lastPointerPos.remove(id)
    }

    fun endAllPointers() {
        activeStrokes.keys.toList().forEach(::onPointerUp)
        lastPointerPos.clear()
    }

    fun zoomBy(factor: Float) {
        val previous = scale
        val next = (previous * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = next / previous
        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        offset = center - (center - offset) * ratio
        scale = next
        markDirty()
    }

    fun resetView() {
        scale = 1f
        offset = Offset.Zero
        markDirty()
    }

    fun clearAll() {
        strokes.clear()
        activeStrokes.clear()
        markDirty()
    }

    // ---- history --------------------------------------------------------------------------

    /** Called when the history sheet opens: flush current drawing, then reload the list. */
    fun refreshHistory() {
        viewModelScope.launch {
            persist()
            reloadHistory()
        }
    }

    fun openDrawing(id: String) {
        if (id == currentDrawingId) return
        viewModelScope.launch {
            persist()
            ready = false
            loadFromDisk(id)?.let(::apply)
            ready = true
        }
    }

    fun newDrawing() {
        if (strokes.isEmpty()) return
        viewModelScope.launch {
            persist()
            startBlank()
        }
    }

    fun deleteDrawing(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO + NonCancellable) {
                ioLock.withLock { runCatching { repository.delete(id) } }
            }
            if (id == currentDrawingId) startBlank()
            reloadHistory()
        }
    }

    /** Save immediately (called from Activity.onStop). */
    fun saveNow() {
        viewModelScope.launch { persist() }
    }

    // ---- internals ------------------------------------------------------------------------

    private fun markDirty() {
        saveRequests.trySend(Unit)
    }

    private fun startBlank() {
        strokes.clear()
        activeStrokes.clear()
        lastPointerPos.clear()
        scale = 1f
        offset = Offset.Zero
        val id = UUID.randomUUID().toString()
        currentDrawingId = id
        savedState[KEY_DRAWING_ID] = id
    }

    private fun apply(data: DrawingData) {
        activeStrokes.clear()
        lastPointerPos.clear()
        strokes.clear()
        strokes.addAll(data.strokes)
        scale = data.scale.coerceIn(MIN_SCALE, MAX_SCALE)
        offset = Offset(data.offsetX, data.offsetY)
        currentDrawingId = data.id
        savedState[KEY_DRAWING_ID] = data.id
    }

    private suspend fun persist() {
        if (!ready) return
        val data = DrawingData(currentDrawingId, scale, offset.x, offset.y, strokes.toList())
        withContext(Dispatchers.IO + NonCancellable) {
            ioLock.withLock {
                runCatching {
                    if (data.strokes.isEmpty()) repository.delete(data.id) else repository.save(data)
                }.onFailure { Log.e(TAG, "Gagal menyimpan coretan ${data.id}", it) }
            }
        }
    }

    private suspend fun loadFromDisk(id: String): DrawingData? =
        withContext(Dispatchers.IO) {
            ioLock.withLock { repository.load(id) }
        }

    private suspend fun reloadHistory() {
        history = withContext(Dispatchers.IO) {
            ioLock.withLock { runCatching { repository.list() }.getOrDefault(emptyList()) }
        }
    }

    private companion object {
        const val TAG = "CuratCoret"
        const val KEY_DRAWING_ID = "drawing_id"
        const val SAVE_DELAY_MS = 400L
        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 10f
    }
}

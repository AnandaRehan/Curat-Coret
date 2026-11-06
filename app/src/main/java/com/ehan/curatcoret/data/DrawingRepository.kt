package com.ehan.curatcoret.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * File based storage: one binary file per drawing (<id>.bin) plus a small index.json
 * for the history list. Not thread-safe by itself: callers serialize access.
 */
class DrawingRepository(private val dir: File) {

    private val indexFile = File(dir, "index.json")

    init {
        dir.mkdirs()
    }

    private fun fileFor(id: String) = File(dir, "$id.bin")

    fun save(data: DrawingData) {
        writeAtomic(fileFor(data.id)) { out ->
            val dos = DataOutputStream(out) // jangan di-close: writeAtomic yang flush, sync, close
            dos.writeInt(FORMAT_VERSION)
            dos.writeFloat(data.scale)
            dos.writeFloat(data.offsetX)
            dos.writeFloat(data.offsetY)
            dos.writeInt(data.strokes.size)
            for (stroke in data.strokes) {
                dos.writeInt(stroke.xy.size)
                for (v in stroke.xy) dos.writeFloat(v)
            }
            dos.flush()
        }
        val index = readIndex().filter { it.id != data.id }.toMutableList()
        index.add(HistoryEntry(data.id, System.currentTimeMillis(), data.strokes.size))
        writeIndex(index)
    }

    fun load(id: String): DrawingData? {
        val file = fileFor(id)
        if (!file.exists()) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { dis ->
                require(dis.readInt() == FORMAT_VERSION)
                val scale = dis.readFloat()
                val offsetX = dis.readFloat()
                val offsetY = dis.readFloat()
                val count = dis.readInt()
                require(count in 0..MAX_STROKES)
                val strokes = ArrayList<InkStroke>(count)
                repeat(count) {
                    val len = dis.readInt()
                    require(len in 0..MAX_FLOATS_PER_STROKE)
                    val xy = FloatArray(len)
                    for (i in 0 until len) xy[i] = dis.readFloat()
                    strokes.add(InkStroke(xy))
                }
                DrawingData(id, scale, offsetX, offsetY, strokes)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun list(): List<HistoryEntry> =
        readIndex()
            .filter { fileFor(it.id).exists() }
            .sortedByDescending { it.updatedAt }

    fun delete(id: String) {
        val file = fileFor(id)
        val existed = file.exists()
        file.delete()
        val index = readIndex()
        if (existed || index.any { it.id == id }) {
            writeIndex(index.filter { it.id != id })
        }
    }

    private fun readIndex(): List<HistoryEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(indexFile.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    id = o.getString("id"),
                    updatedAt = o.getLong("updatedAt"),
                    strokeCount = o.getInt("strokeCount"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("updatedAt", it.updatedAt)
                    .put("strokeCount", it.strokeCount),
            )
        }
        writeAtomic(indexFile) { it.write(arr.toString().toByteArray()) }
    }

    /** Write to a temp file, then move over the target so a crash never leaves half a file. */
    private fun writeAtomic(target: File, block: (BufferedOutputStream) -> Unit) {
        val tmp = File(dir, target.name + ".tmp")
        FileOutputStream(tmp).use { fos ->
            val buffered = BufferedOutputStream(fos)
            block(buffered)
            buffered.flush()
            fos.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val MAX_STROKES = 1_000_000
        const val MAX_FLOATS_PER_STROKE = 10_000_000
    }
}

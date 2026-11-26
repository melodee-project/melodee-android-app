package org.melodee.android.artwork

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.melodee.android.MelodeeApplication
import java.io.File
import java.io.FileNotFoundException

/** Read-only, allowlisted artwork for car hosts. Never exposes cache or audio paths. */
class ArtworkProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = "image/png"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r" || uri.pathSegments.size != 1) throw FileNotFoundException()
        val app = context?.applicationContext as? MelodeeApplication ?: throw FileNotFoundException()
        val album = app.catalog.album(uri.lastPathSegment.orEmpty()) ?: throw FileNotFoundException()
        val directory = File(app.cacheDir, "demo-artwork").apply { mkdirs() }
        val file = File(directory, "${album.id}.png")
        synchronized(this) {
            if (!file.exists()) {
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                val drawable = requireNotNull(app.getDrawable(album.artwork))
                drawable.setBounds(0, 0, 512, 512)
                drawable.draw(Canvas(bitmap))
                val temporary = File(directory, "${album.id}.tmp")
                temporary.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                if (!temporary.renameTo(file)) { temporary.delete(); throw FileNotFoundException() }
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
}


package sene1453studio.lord.of.linux

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class AssetManager(private val context: Context) {

    companion object {
        const val DIR_SOUNDS = "sounds/custom"
        const val DIR_IMAGES = "images/custom"
        const val EXT_AUDIO = ".audio"
        const val EXT_IMAGE = ".img"

        @Volatile
        private var instance: AssetManager? = null
        fun getInstance(context: Context): AssetManager {
            return instance ?: synchronized(this) {
                instance ?: AssetManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun importAsset(uri: Uri, category: String): File? {
        return try {
            val isImage = category == GameManager.CAT_LOGO
            val subDir = if (isImage) DIR_IMAGES else DIR_SOUNDS
            val ext = if (isImage) EXT_IMAGE else EXT_AUDIO
            val filename = "asset_${category}_${System.currentTimeMillis()}$ext"

            val dir = File(context.filesDir, subDir)
            if (!dir.exists()) dir.mkdirs()

            val outFile = File(dir, filename)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteAssetFile(uriPath: String?) {
        if (uriPath == null) return
        try {
            val file = File(Uri.parse(uriPath).path ?: return)
            if (file.exists() && file.absolutePath.contains(context.packageName)) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


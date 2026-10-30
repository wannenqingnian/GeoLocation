package io.github.ceigt.geolocation.manager.map

import android.content.Context
import android.webkit.WebStorage
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.modules.SqlTileWriter
import java.io.File

/** Clears map cache/storage when the user explicitly requests it. */
object WebMapCache {
    suspend fun clear(context: Context): Boolean {
        WebView(context.applicationContext).apply {
            clearCache(true)
            clearHistory()
            destroy()
        }
        WebStorage.getInstance().deleteAllData()
        return withContext(Dispatchers.IO) {
            if (!File(context.cacheDir, "osmdroid/tiles/cache.db").exists()) true
            else SqlTileWriter().purgeCache()
        }
    }
}

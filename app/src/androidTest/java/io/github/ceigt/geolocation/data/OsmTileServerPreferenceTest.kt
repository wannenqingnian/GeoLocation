package io.github.ceigt.geolocation.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import io.github.ceigt.geolocation.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OsmTileServerPreferenceTest {
    @Test fun serverSelectionIsDurableAndDoesNotChangeTheMapProvider() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = base.getSharedPreferences("osm_tile_server_test", Context.MODE_PRIVATE)
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }
        try {
            prefs.edit().clear().putString(KEY_MAP_PROVIDER, "osmdroid").commit()
            val repository = PreferencesRepository(context)
            assertEquals(OsmTileServer.OPENSTREETMAP, repository.getOsmTileServerFlow().first())
            repository.saveOsmTileServer(OsmTileServer.AUTONAVI)
            assertEquals(OsmTileServer.AUTONAVI, PreferencesRepository(context).getOsmTileServerFlow().first())
            assertEquals("osmdroid", prefs.getString(KEY_MAP_PROVIDER, null))
        } finally { prefs.edit().clear().commit() }
    }
}

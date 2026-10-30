package io.github.ceigt.geolocation.data

enum class OsmTileServer(val storedValue: String) {
    OPENSTREETMAP("openstreetmap"),
    AUTONAVI("autonavi");

    companion object {
        fun fromStored(value: String?): OsmTileServer =
            entries.firstOrNull { it.storedValue == value } ?: OPENSTREETMAP
    }
}

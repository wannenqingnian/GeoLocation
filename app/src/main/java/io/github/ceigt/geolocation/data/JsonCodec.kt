package io.github.ceigt.geolocation.data

import io.github.ceigt.geolocation.data.model.FavoriteLocation
import io.github.ceigt.geolocation.data.model.LastClickedLocation
import org.json.JSONArray
import org.json.JSONObject

/** Small, reflection-free codecs for the few preference payloads shared by manager and hooks. */
object JsonCodec {
    fun encodeLocation(location: LastClickedLocation): String = JSONObject()
        .put("latitude", location.latitude)
        .put("longitude", location.longitude)
        .toString()

    fun decodeLocation(json: String): LastClickedLocation {
        val value = JSONObject(json)
        return LastClickedLocation(
            latitude = value.getDouble("latitude"),
            longitude = value.getDouble("longitude")
        )
    }

    fun encodeStrings(values: Collection<String>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }

    fun decodeStringSet(json: String): Set<String> {
        val array = JSONArray(json)
        return buildSet(array.length()) {
            repeat(array.length()) { index ->
                array.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    fun encodeStringMap(values: Map<String, String>): String {
        val value = JSONObject()
        values.toSortedMap().forEach(value::put)
        return value.toString()
    }

    fun decodeStringMap(json: String): Map<String, String> {
        val value = JSONObject(json)
        return buildMap {
            value.keys().forEach { key ->
                value.optString(key).takeIf(String::isNotBlank)?.let { put(key, it) }
            }
        }
    }

    fun encodeFavorites(values: List<FavoriteLocation>): String {
        val array = JSONArray()
        values.forEach { favorite ->
            array.put(
                JSONObject()
                    .put("name", favorite.name)
                    .put("address", favorite.address)
                    .put("latitude", favorite.latitude)
                    .put("longitude", favorite.longitude)
            )
        }
        return array.toString()
    }

    fun decodeFavorites(json: String): List<FavoriteLocation> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val value = array.getJSONObject(index)
                val latitude = value.getDouble("latitude")
                val longitude = value.getDouble("longitude")
                require(latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0)
                add(
                    FavoriteLocation(
                        name = value.getString("name"),
                        address = value.optString("address", ""),
                        latitude = latitude,
                        longitude = longitude
                    )
                )
            }
        }
    }

    fun encodeAddressMetadata(meta: io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata): String {
        return JSONObject()
            .put("formattedAddress", meta.formattedAddress)
            .put("poiName", meta.poiName)
            .put("country", meta.country)
            .put("province", meta.province)
            .put("city", meta.city)
            .put("district", meta.district)
            .put("street", meta.street)
            .put("streetNum", meta.streetNum)
            .put("adCode", meta.adCode)
            .put("cityCode", meta.cityCode)
            .toString()
    }

    fun decodeAddressMetadata(json: String?): io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata {
        if (json.isNullOrBlank()) return io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata()
        return runCatching {
            val obj = JSONObject(json)
            io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata(
                formattedAddress = obj.optString("formattedAddress", ""),
                poiName = obj.optString("poiName", ""),
                country = obj.optString("country", "中国"),
                province = obj.optString("province", ""),
                city = obj.optString("city", ""),
                district = obj.optString("district", ""),
                street = obj.optString("street", ""),
                streetNum = obj.optString("streetNum", ""),
                adCode = obj.optString("adCode", ""),
                cityCode = obj.optString("cityCode", "")
            )
        }.getOrDefault(io.github.ceigt.geolocation.data.model.channel.LocationAddressMetadata())
    }

    fun encodeChannelSettingsMap(settingsMap: Map<String, io.github.ceigt.geolocation.data.model.channel.ChannelSettings>): String {
        val root = JSONObject()
        settingsMap.forEach { (channelKey, settings) ->
            val obj = JSONObject()
                .put("enabled", settings.enabled)
                .put("activeRefreshEnabled", settings.activeRefreshEnabled)
                .put("activeRefreshIntervalMillis", settings.activeRefreshIntervalMillis)
                .put("addressInfoPolicy", settings.addressInfoPolicy.storedValue)
                .put("accuracyFallbackPolicy", settings.accuracyFallbackPolicy.storedValue)
            root.put(channelKey, obj)
        }
        return root.toString()
    }

    fun decodeChannelSettingsMap(json: String?): Map<String, io.github.ceigt.geolocation.data.model.channel.ChannelSettings> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            val result = mutableMapOf<String, io.github.ceigt.geolocation.data.model.channel.ChannelSettings>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                result[key] = io.github.ceigt.geolocation.data.model.channel.ChannelSettings(
                    enabled = obj.optBoolean("enabled", true),
                    activeRefreshEnabled = obj.optBoolean("activeRefreshEnabled", true),
                    activeRefreshIntervalMillis = obj.optLong("activeRefreshIntervalMillis", 1000L),
                    addressInfoPolicy = io.github.ceigt.geolocation.data.model.channel.AddressInfoPolicy.fromStored(obj.optString("addressInfoPolicy")),
                    accuracyFallbackPolicy = io.github.ceigt.geolocation.data.model.channel.AccuracyFallbackPolicy.fromStored(obj.optString("accuracyFallbackPolicy"))
                )
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun encodeCadenceConfigs(configs: Map<String, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig>): String {
        val root = JSONObject()
        configs.forEach { (pkg, cfg) ->
            val obj = JSONObject()
                .put("mode", cfg.mode.storedValue)
                .put("spm", cfg.customStepsPerMinute ?: -1)
                .put("channel", cfg.channel.storedValue)
            root.put(pkg, obj)
        }
        return root.toString()
    }

    fun decodeCadenceConfigs(json: String?): Map<String, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            val result = mutableMapOf<String, io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg) ?: continue
                val mode = io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.fromStored(obj.optString("mode"))
                val spmRaw = obj.optInt("spm", -1)
                val spm = if (spmRaw > 0) spmRaw else null
                val channel = io.github.ceigt.geolocation.data.model.cadence.TargetCadenceChannel.fromStored(obj.optString("channel"))
                result[pkg] = io.github.ceigt.geolocation.data.model.cadence.TargetCadenceConfig(mode, spm, channel)
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun encodeBleProfiles(profiles: List<io.github.ceigt.geolocation.domain.ble.BleProfile>): String {
        val array = JSONArray()
        for (profile in profiles) {
            val pObj = JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("updatedAt", profile.updatedAtEpochMillis)

            val devArray = JSONArray()
            for (dev in profile.devices) {
                val devObj = JSONObject()
                    .put("id", dev.id)
                    .put("displayName", dev.displayName)
                    .put("virtualAddress", dev.virtualAddress)
                    .put("baseRssi", dev.signal.baseRssi)
                devArray.put(devObj)
            }
            pObj.put("devices", devArray)
            array.put(pObj)
        }
        return array.toString()
    }

    fun decodeBleProfiles(json: String?): List<io.github.ceigt.geolocation.domain.ble.BleProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            val list = mutableListOf<io.github.ceigt.geolocation.domain.ble.BleProfile>()
            for (i in 0 until array.length()) {
                val pObj = array.getJSONObject(i)
                val id = pObj.getString("id")
                val name = pObj.getString("name")
                val updated = pObj.optLong("updatedAt", System.currentTimeMillis())

                val devList = mutableListOf<io.github.ceigt.geolocation.domain.ble.BleDeviceProfile>()
                val devArray = pObj.optJSONArray("devices")
                if (devArray != null) {
                    for (j in 0 until devArray.length()) {
                        val dObj = devArray.getJSONObject(j)
                        devList.add(
                            io.github.ceigt.geolocation.domain.ble.BleDeviceProfile(
                                id = dObj.optString("id", java.util.UUID.randomUUID().toString()),
                                displayName = dObj.optString("displayName", "Virtual Beacon"),
                                virtualAddress = dObj.optString("virtualAddress", "FA:CE:88:99:00:11"),
                                signal = io.github.ceigt.geolocation.domain.ble.BleSignalProfile(
                                    baseRssi = dObj.optInt("baseRssi", -58)
                                )
                            )
                        )
                    }
                }
                list.add(io.github.ceigt.geolocation.domain.ble.BleProfile(id, name, devList, updated))
            }
            list
        }.getOrDefault(emptyList())
    }

    fun encodeNfcProfiles(profiles: List<io.github.ceigt.geolocation.domain.nfc.NfcProfile>): String {
        val array = JSONArray()
        for (profile in profiles) {
            val obj = JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("targetPackage", profile.targetPackage)
                .put("targetLabel", profile.targetLabel)
                .put("primaryUri", profile.primaryUri)
                .put("allowViewFallback", profile.allowViewFallback)
                .put("updatedAt", profile.updatedAtEpochMillis)
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeNfcProfiles(json: String?): List<io.github.ceigt.geolocation.domain.nfc.NfcProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            val list = mutableListOf<io.github.ceigt.geolocation.domain.nfc.NfcProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    io.github.ceigt.geolocation.domain.nfc.NfcProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        targetPackage = obj.optString("targetPackage", ""),
                        targetLabel = obj.optString("targetLabel", ""),
                        primaryUri = obj.getString("primaryUri"),
                        allowViewFallback = obj.optBoolean("allowViewFallback", true),
                        updatedAtEpochMillis = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun encodeEnvironmentProfiles(profiles: List<io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile>): String {
        val array = JSONArray()
        for (profile in profiles) {
            val pObj = JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("latitude", profile.latitude ?: JSONObject.NULL)
                .put("longitude", profile.longitude ?: JSONObject.NULL)
                .put("altitude", profile.altitude ?: JSONObject.NULL)
                .put("wifiEnabled", profile.wifiEnabled)
                .put("cellEnabled", profile.cellEnabled)
                .put("bleEnabled", profile.bleEnabled)
                .put("gnssEnabled", profile.gnssEnabled)
                .put("suppressRealScan", profile.suppressRealScan)
                .put("createdAt", profile.createdAtEpochMillis)

            val wifiArr = JSONArray()
            for (ap in profile.wifiAps) {
                wifiArr.put(
                    JSONObject()
                        .put("ssid", ap.ssid)
                        .put("bssid", ap.bssid)
                        .put("capabilities", ap.capabilities)
                        .put("levelDbm", ap.levelDbm)
                        .put("frequencyMhz", ap.frequencyMhz)
                )
            }
            pObj.put("wifiAps", wifiArr)

            val cellArr = JSONArray()
            for (c in profile.cellTowers) {
                cellArr.put(
                    JSONObject()
                        .put("radioType", c.radioType)
                        .put("mcc", c.mcc)
                        .put("mnc", c.mnc)
                        .put("lacOrTac", c.lacOrTac)
                        .put("cidOrCi", c.cidOrCi)
                        .put("pci", c.pci)
                        .put("arfcn", c.arfcn)
                        .put("rssiDbm", c.rssiDbm)
                )
            }
            pObj.put("cellTowers", cellArr)

            val bleArr = JSONArray()
            for (b in profile.bleBeacons) {
                bleArr.put(
                    JSONObject()
                        .put("id", b.id)
                        .put("displayName", b.displayName)
                        .put("virtualAddress", b.virtualAddress)
                        .put("baseRssi", b.signal.baseRssi)
                )
            }
            pObj.put("bleBeacons", bleArr)

            val satArr = JSONArray()
            for (s in profile.satellites) {
                satArr.put(
                    JSONObject()
                        .put("svid", s.svid)
                        .put("constellationType", s.constellationType)
                        .put("cn0DbHz", s.cn0DbHz.toDouble())
                        .put("elevationDegrees", s.elevationDegrees.toDouble())
                        .put("azimuthDegrees", s.azimuthDegrees.toDouble())
                        .put("usedInFix", s.usedInFix)
                )
            }
            pObj.put("satellites", satArr)

            array.put(pObj)
        }
        return array.toString()
    }

    fun decodeEnvironmentProfiles(json: String?): List<io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            val list = mutableListOf<io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile>()
            for (i in 0 until array.length()) {
                val pObj = array.getJSONObject(i)
                val id = pObj.getString("id")
                val name = pObj.getString("name")
                val lat = if (pObj.isNull("latitude")) null else pObj.optDouble("latitude")
                val lon = if (pObj.isNull("longitude")) null else pObj.optDouble("longitude")
                val alt = if (pObj.isNull("altitude")) null else pObj.optDouble("altitude")
                val wifiEnabled = pObj.optBoolean("wifiEnabled", true)
                val cellEnabled = pObj.optBoolean("cellEnabled", true)
                val bleEnabled = pObj.optBoolean("bleEnabled", true)
                val gnssEnabled = pObj.optBoolean("gnssEnabled", true)
                val suppressRealScan = pObj.optBoolean("suppressRealScan", true)
                val createdAt = pObj.optLong("createdAt", System.currentTimeMillis())

                val wifiAps = mutableListOf<io.github.ceigt.geolocation.data.model.environment.VirtualWifiAp>()
                val wifiArr = pObj.optJSONArray("wifiAps")
                if (wifiArr != null) {
                    for (w in 0 until wifiArr.length()) {
                        val wObj = wifiArr.getJSONObject(w)
                        wifiAps.add(
                            io.github.ceigt.geolocation.data.model.environment.VirtualWifiAp(
                                ssid = wObj.getString("ssid"),
                                bssid = wObj.getString("bssid"),
                                capabilities = wObj.optString("capabilities", "[WPA2-PSK-CCMP][ESS]"),
                                levelDbm = wObj.optInt("levelDbm", -55),
                                frequencyMhz = wObj.optInt("frequencyMhz", 2437)
                            )
                        )
                    }
                }

                val cellTowers = mutableListOf<io.github.ceigt.geolocation.data.model.environment.VirtualCellTower>()
                val cellArr = pObj.optJSONArray("cellTowers")
                if (cellArr != null) {
                    for (c in 0 until cellArr.length()) {
                        val cObj = cellArr.getJSONObject(c)
                        cellTowers.add(
                            io.github.ceigt.geolocation.data.model.environment.VirtualCellTower(
                                radioType = cObj.optString("radioType", "LTE"),
                                mcc = cObj.optInt("mcc", 460),
                                mnc = cObj.optInt("mnc", 0),
                                lacOrTac = cObj.optInt("lacOrTac", 4192),
                                cidOrCi = cObj.optLong("cidOrCi", 135792468L),
                                pci = cObj.optInt("pci", 100),
                                arfcn = cObj.optInt("arfcn", 1850),
                                rssiDbm = cObj.optInt("rssiDbm", -75)
                            )
                        )
                    }
                }

                val bleBeacons = mutableListOf<io.github.ceigt.geolocation.domain.ble.BleDeviceProfile>()
                val bleArr = pObj.optJSONArray("bleBeacons")
                if (bleArr != null) {
                    for (b in 0 until bleArr.length()) {
                        val bObj = bleArr.getJSONObject(b)
                        bleBeacons.add(
                            io.github.ceigt.geolocation.domain.ble.BleDeviceProfile(
                                id = bObj.optString("id", java.util.UUID.randomUUID().toString()),
                                displayName = bObj.optString("displayName", "BLE Beacon"),
                                virtualAddress = bObj.optString("virtualAddress", "FA:CE:88:99:00:11"),
                                signal = io.github.ceigt.geolocation.domain.ble.BleSignalProfile(
                                    baseRssi = bObj.optInt("baseRssi", -65)
                                )
                            )
                        )
                    }
                }

                val satellites = mutableListOf<io.github.ceigt.geolocation.data.model.environment.VirtualSatellite>()
                val satArr = pObj.optJSONArray("satellites")
                if (satArr != null) {
                    for (s in 0 until satArr.length()) {
                        val sObj = satArr.getJSONObject(s)
                        satellites.add(
                            io.github.ceigt.geolocation.data.model.environment.VirtualSatellite(
                                svid = sObj.getInt("svid"),
                                constellationType = sObj.optInt("constellationType", 1),
                                cn0DbHz = sObj.optDouble("cn0DbHz", 32.0).toFloat(),
                                elevationDegrees = sObj.optDouble("elevationDegrees", 45.0).toFloat(),
                                azimuthDegrees = sObj.optDouble("azimuthDegrees", 120.0).toFloat(),
                                usedInFix = sObj.optBoolean("usedInFix", true)
                            )
                        )
                    }
                }

                list.add(io.github.ceigt.geolocation.data.model.environment.EnvironmentProfile(
                    id = id,
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    altitude = alt,
                    wifiEnabled = wifiEnabled,
                    cellEnabled = cellEnabled,
                    bleEnabled = bleEnabled,
                    gnssEnabled = gnssEnabled,
                    suppressRealScan = suppressRealScan,
                    wifiAps = wifiAps,
                    cellTowers = cellTowers,
                    bleBeacons = bleBeacons,
                    satellites = satellites,
                    createdAtEpochMillis = createdAt
                ))
            }
            list
        }.getOrDefault(emptyList())
    }

    fun encodeRouteFavorites(routes: List<io.github.ceigt.geolocation.data.model.route.RouteFavorite>): String {
        val array = JSONArray()
        for (rf in routes) {
            val obj = JSONObject()
                .put("id", rf.id)
                .put("name", rf.name)
                .put("loopMode", rf.loopMode.name)
                .put("travelMode", rf.travelMode.name)
                .put("speedMps", rf.speedMps)
                .put("cadenceMode", rf.cadenceMode.storedValue)
                .put("customSpm", rf.customSpm)
                .put("createdAt", rf.createdAtEpochMillis)

            val wpArr = JSONArray()
            for (wp in rf.waypoints) {
                wpArr.put(
                    JSONObject()
                        .put("name", wp.name)
                        .put("latitude", wp.latitude)
                        .put("longitude", wp.longitude)
                        .put("stopDurationSeconds", wp.stopDurationSeconds)
                )
            }
            obj.put("waypoints", wpArr)
            array.put(obj)
        }
        return array.toString()
    }

    fun decodeRouteFavorites(json: String?): List<io.github.ceigt.geolocation.data.model.route.RouteFavorite> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            val list = mutableListOf<io.github.ceigt.geolocation.data.model.route.RouteFavorite>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val loopMode = io.github.ceigt.geolocation.data.model.route.RouteLoopMode.fromName(obj.optString("loopMode"))
                val travelMode = io.github.ceigt.geolocation.data.model.route.RouteTravelMode.fromName(obj.optString("travelMode"))
                val speedMps = obj.optDouble("speedMps", travelMode.defaultSpeedMps)
                val cadenceMode = io.github.ceigt.geolocation.data.model.cadence.TargetCadenceMode.fromStored(obj.optString("cadenceMode", "auto"))
                val customSpm = obj.optInt("customSpm", 165)
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())

                val waypoints = mutableListOf<io.github.ceigt.geolocation.data.model.route.RouteWaypoint>()
                val wpArr = obj.optJSONArray("waypoints")
                if (wpArr != null) {
                    for (j in 0 until wpArr.length()) {
                        val wObj = wpArr.getJSONObject(j)
                        waypoints.add(
                            io.github.ceigt.geolocation.data.model.route.RouteWaypoint(
                                name = wObj.optString("name", ""),
                                latitude = wObj.getDouble("latitude"),
                                longitude = wObj.getDouble("longitude"),
                                stopDurationSeconds = wObj.optInt("stopDurationSeconds", 0)
                            )
                        )
                    }
                }
                list.add(
                    io.github.ceigt.geolocation.data.model.route.RouteFavorite(
                        id = id,
                        name = name,
                        waypoints = waypoints,
                        loopMode = loopMode,
                        travelMode = travelMode,
                        speedMps = speedMps,
                        cadenceMode = cadenceMode,
                        customSpm = customSpm,
                        createdAtEpochMillis = createdAt
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }
}

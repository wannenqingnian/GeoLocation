package io.github.ceigt.geolocation.xposed.hooks

import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.os.Build
import android.os.SystemClock
import android.telephony.CellIdentityLte
import android.telephony.CellInfo
import android.telephony.CellInfoLte
import android.telephony.CellSignalStrengthLte
import io.github.ceigt.geolocation.data.model.environment.VirtualCellTower
import io.github.ceigt.geolocation.data.model.environment.VirtualWifiAp
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.Random

object VirtualEnvironmentFactory {
    private val random = Random()

    fun createSyntheticScanResults(aps: List<VirtualWifiAp>): List<ScanResult> {
        if (aps.isEmpty()) return emptyList()
        val results = mutableListOf<ScanResult>()

        for (ap in aps) {
            runCatching {
                val scanResult = createScanResultInstance()
                if (scanResult != null) {
                    setField(scanResult, "SSID", ap.ssid)
                    setField(scanResult, "BSSID", ap.bssid)
                    setField(scanResult, "capabilities", ap.capabilities)
                    // Level jitter +- 3 dBm
                    val jitteredLevel = ap.levelDbm + random.nextInt(7) - 3
                    setField(scanResult, "level", jitteredLevel)
                    setField(scanResult, "frequency", ap.frequencyMhz)
                    setField(scanResult, "timestamp", SystemClock.elapsedRealtime() * 1000L)
                    results.add(scanResult)
                }
            }
        }
        return results
    }

    fun createSyntheticCellInfos(towers: List<VirtualCellTower>): List<CellInfo> {
        if (towers.isEmpty()) return emptyList()
        val results = mutableListOf<CellInfo>()

        for (tower in towers) {
            runCatching {
                val cellInfoLte = createCellInfoLteInstance(tower)
                if (cellInfoLte != null) results.add(cellInfoLte)
            }
        }
        return results
    }

    private fun createScanResultInstance(): ScanResult? {
        return runCatching {
            val constructor: Constructor<ScanResult> = ScanResult::class.java.getDeclaredConstructor().apply {
                isAccessible = true
            }
            constructor.newInstance()
        }.getOrNull()
    }

    private fun createCellInfoLteInstance(tower: VirtualCellTower): CellInfoLte? {
        return runCatching {
            val cellInfo = CellInfoLte::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

            // Construct CellIdentityLte
            val idLte = runCatching {
                // Android 10+ (Q+): CellIdentityLte(pci, tac, earfcn, bands, mccStr, mncStr, alphaLong, alphaShort, addlPlmns, csgInfo)
                val ctor = CellIdentityLte::class.java.declaredConstructors.firstOrNull { it.parameterTypes.isNotEmpty() }
                ctor?.isAccessible = true
                if (ctor != null) {
                    val pTypes = ctor.parameterTypes
                    val args = arrayOfNulls<Any>(pTypes.size)
                    for (i in pTypes.indices) {
                        when (pTypes[i]) {
                            Int::class.javaPrimitiveType -> args[i] = when (i) {
                                0 -> tower.pci
                                1 -> tower.lacOrTac
                                2 -> tower.arfcn
                                else -> 0
                            }
                            String::class.java -> args[i] = when (i) {
                                4 -> tower.mcc.toString()
                                5 -> tower.mnc.toString()
                                else -> ""
                            }
                            else -> args[i] = null
                        }
                    }
                    ctor.newInstance(*args) as? CellIdentityLte
                } else {
                    CellIdentityLte::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                }
            }.getOrElse {
                CellIdentityLte::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            }

            if (idLte != null) {
                setField(cellInfo, "mCellIdentityLte", idLte)
            }

            // Construct CellSignalStrengthLte
            val signalLte = runCatching {
                CellSignalStrengthLte::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance().apply {
                    setField(this, "mRssi", tower.rssiDbm)
                    setField(this, "mRsrp", tower.rssiDbm)
                }
            }.getOrNull()

            if (signalLte != null) {
                setField(cellInfo, "mCellSignalStrengthLte", signalLte)
            }

            cellInfo
        }.getOrNull()
    }

    private fun setField(target: Any, fieldName: String, value: Any?) {
        runCatching {
            var current: Class<*>? = target.javaClass
            var field: Field? = null
            while (current != null && current != Any::class.java) {
                try {
                    field = current.getDeclaredField(fieldName).apply { isAccessible = true }
                    break
                } catch (_: NoSuchFieldException) {
                    current = current.superclass
                }
            }
            if (field == null) {
                field = HiddenApiBypass.getInstanceFields(target.javaClass)
                    .firstOrNull { (it as? Field)?.name == fieldName } as? Field
                field?.isAccessible = true
            }
            field?.set(target, value)
        }
    }
}

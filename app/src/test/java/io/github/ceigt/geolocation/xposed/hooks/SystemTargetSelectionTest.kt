package io.github.ceigt.geolocation.xposed.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemTargetSelectionTest {
    @Test fun managerQueriesAreNeverReplaced() {
        assertNull(selectSystemTargetPackage(setOf("io.github.ceigt.geolocation"), emptySet()))
    }
    @Test fun systemOnlyScopeStillSelectsThirdPartyCaller() {
        val result = selectSystemTargetPackage(
            linkedSetOf("android", "com.android.phone", "com.eg.android.AlipayGphone"),
            setOf("system", "android", "com.android.phone")
        )
        assertEquals("com.eg.android.AlipayGphone", result)
    }

    @Test fun explicitThirdPartyTargetWinsForCoordinateSelection() {
        val result = selectSystemTargetPackage(
            linkedSetOf("com.google.android.gms", "com.tencent.wework"),
            setOf("system", "com.tencent.wework")
        )
        assertEquals("com.tencent.wework", result)
    }

    @Test fun frameworkOnlyDeliveryIsNotModified() {
        assertNull(selectSystemTargetPackage(
            setOf("android", "com.android.location.fused", "com.android.phone"),
            setOf("system", "android", "com.android.phone")
        ))
    }

    @Test fun ambiguousSharedUidIsNotGuessed() {
        assertNull(selectSystemTargetPackage(
            linkedSetOf("com.example.one", "com.example.two"),
            setOf("system", "android")
        ))
    }

    @Test fun oneExplicitSharedUidTargetCanBeAttributed() {
        assertEquals("com.example.two", selectSystemTargetPackage(
            linkedSetOf("com.example.one", "com.example.two"),
            setOf("system", "com.example.two")
        ))
    }

    @Test fun globalModeIgnoresConfiguredAppList() {
        assertEquals("com.tencent.mm", selectSystemTargetPackage(
            setOf("com.tencent.mm"),
            setOf("system", "android"),
            global = true
        ))
    }

    @Test fun globalModeResolvesSharedUidDeterministically() {
        assertEquals("com.example.one", selectSystemTargetPackage(
            linkedSetOf("com.example.two", "com.example.one"),
            setOf("system"),
            global = true
        ))
    }

    @Test fun globalModeNeverSelectsManagerOrFramework() {
        assertNull(selectSystemTargetPackage(
            linkedSetOf("android", "com.android.phone", "io.github.ceigt.geolocation"),
            emptySet(),
            global = true
        ))
    }

    @Test fun globalModePrefersConfiguredTargetForCoordinates() {
        assertEquals("com.example.two", selectSystemTargetPackage(
            linkedSetOf("com.example.one", "com.example.two"),
            setOf("com.example.two"),
            global = true
        ))
    }
}

package io.github.ceigt.geolocation.data.model.channel

enum class LocationChannel(val channelKey: String) {
    ANDROID_SYSTEM("android_system"),
    GOOGLE_FUSED("google_fused"),
    HUAWEI_FUSED("huawei_fused"),
    AMAP("amap_sdk"),
    BAIDU("baidu_sdk"),
    TENCENT("tencent_sdk");

    companion object {
        fun fromKey(key: String?): LocationChannel =
            entries.firstOrNull { it.channelKey == key } ?: ANDROID_SYSTEM
    }
}

enum class AddressInfoPolicy(val storedValue: String) {
    PRESERVE("preserve"),
    AUTO_FILL("auto_fill");

    companion object {
        fun fromStored(value: String?): AddressInfoPolicy =
            entries.firstOrNull { it.storedValue == value } ?: PRESERVE
    }
}

enum class AccuracyFallbackPolicy(val storedValue: String) {
    PRESERVE("preserve"),
    AUTO_FILL("auto_fill"),
    CUSTOM("custom");

    companion object {
        fun fromStored(value: String?): AccuracyFallbackPolicy =
            entries.firstOrNull { it.storedValue == value } ?: AUTO_FILL
    }
}

data class ChannelSettings(
    val enabled: Boolean = true,
    val activeRefreshEnabled: Boolean = true,
    val activeRefreshIntervalMillis: Long = 1000L,
    val addressInfoPolicy: AddressInfoPolicy = AddressInfoPolicy.AUTO_FILL,
    val accuracyFallbackPolicy: AccuracyFallbackPolicy = AccuracyFallbackPolicy.AUTO_FILL
)

data class LocationAddressMetadata(
    val formattedAddress: String = "",
    val poiName: String = "",
    val country: String = "中国",
    val province: String = "",
    val city: String = "",
    val district: String = "",
    val street: String = "",
    val streetNum: String = "",
    val adCode: String = "",
    val cityCode: String = ""
)

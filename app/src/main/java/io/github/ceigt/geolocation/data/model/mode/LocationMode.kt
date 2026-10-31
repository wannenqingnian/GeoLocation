package io.github.ceigt.geolocation.data.model.mode

enum class LocationMode(val storedValue: String) {
    APPLICATION_HOOK("app_hook"),
    SYSTEM_HOOK("system_hook"),
    GLOBAL_HOOK("global_hook"),
    MOCK_PROVIDER("mock_provider"),
    ROOT_MODE("root_mode");

    companion object {
        fun fromStored(value: String?): LocationMode =
            entries.firstOrNull { it.storedValue == value } ?: APPLICATION_HOOK
    }
}

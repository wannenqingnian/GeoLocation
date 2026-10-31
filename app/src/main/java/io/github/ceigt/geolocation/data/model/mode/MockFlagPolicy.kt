package io.github.ceigt.geolocation.data.model.mode

enum class MockFlagPolicy(val storedValue: String) {
    HIDE_ALL("hide_all"),
    HIDE_INJECTED("hide_injected"),
    PRESERVE("preserve");

    companion object {
        fun fromStored(value: String?): MockFlagPolicy =
            entries.firstOrNull { it.storedValue == value } ?: HIDE_ALL
    }
}

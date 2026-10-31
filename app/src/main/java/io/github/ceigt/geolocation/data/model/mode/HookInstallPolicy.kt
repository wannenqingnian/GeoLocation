package io.github.ceigt.geolocation.data.model.mode

enum class HookInstallPolicy(val storedValue: String) {
    IMMEDIATE("immediate"),
    DYNAMIC_CLASSLOAD("dynamic_classload"),
    APPLICATION_CREATE("application_create");

    companion object {
        fun fromStored(value: String?): HookInstallPolicy =
            entries.firstOrNull { it.storedValue == value } ?: IMMEDIATE
    }
}

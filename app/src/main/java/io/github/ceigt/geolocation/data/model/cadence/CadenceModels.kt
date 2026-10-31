package io.github.ceigt.geolocation.data.model.cadence

enum class TargetCadenceMode(val storedValue: String) {
    INHERIT("inherit"),
    OFF("off"),
    AUTO("auto"),
    CUSTOM("custom");

    companion object {
        fun fromStored(value: String?): TargetCadenceMode =
            entries.firstOrNull { it.storedValue == value } ?: AUTO
    }
}

enum class TargetCadenceChannel(val storedValue: String) {
    STANDARD_STEP_SENSORS("standard_step_sensors"),
    ACCELEROMETER("accelerometer");

    companion object {
        fun fromStored(value: String?): TargetCadenceChannel =
            entries.firstOrNull { it.storedValue == value } ?: ACCELEROMETER
    }
}

data class TargetCadenceConfig(
    val mode: TargetCadenceMode = TargetCadenceMode.AUTO,
    val customStepsPerMinute: Int? = 165,
    val channel: TargetCadenceChannel = TargetCadenceChannel.ACCELEROMETER
)

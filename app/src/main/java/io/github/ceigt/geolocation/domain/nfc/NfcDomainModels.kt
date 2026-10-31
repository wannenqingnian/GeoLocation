package io.github.ceigt.geolocation.domain.nfc

enum class NfcHardwareState {
    AVAILABLE,
    DISABLED,
    UNSUPPORTED
}

enum class NfcUriValidationError {
    EMPTY,
    TOO_LONG,
    CONTROL_CHARACTER,
    MISSING_SCHEME,
    BLOCKED_SCHEME,
    MALFORMED
}

enum class NfcUriWarning {
    CLEARTEXT_HTTP,
    CUSTOM_SCHEME,
    SENSITIVE_PARAMETERS
}

enum class NfcDispatchFailureReason {
    AUTHORIZATION_REQUIRED,
    INVALID_URI,
    TARGET_NOT_INSTALLED,
    NO_NDEF_RECEIVER,
    TARGET_REJECTED,
    APP_NOT_FOREGROUND
}

data class NfcProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val targetPackage: String,
    val targetLabel: String,
    val primaryUri: String,
    val allowViewFallback: Boolean = true,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class NfcReadResult(
    val primaryUri: String,
    val targetPackage: String?,
    val recordCount: Int,
    val byteSize: Int,
    val tagIdHex: String
)

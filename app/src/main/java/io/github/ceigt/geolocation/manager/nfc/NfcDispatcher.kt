package io.github.ceigt.geolocation.manager.nfc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import io.github.ceigt.geolocation.domain.nfc.*

object NfcDispatcher {
    private const val TAG = "NfcDispatcher"

    private val BLOCKED_SCHEMES = setOf("file", "content", "javascript", "data")

    /**
     * Comprehensive URI validation: checks length, control characters, schemes
     */
    fun validateUri(uriString: String?): Pair<NfcUriValidationError?, List<NfcUriWarning>> {
        val warnings = mutableListOf<NfcUriWarning>()
        if (uriString.isNullOrBlank()) {
            return Pair(NfcUriValidationError.EMPTY, emptyList())
        }
        if (uriString.length > 2048) {
            return Pair(NfcUriValidationError.TOO_LONG, emptyList())
        }
        if (uriString.any { it.isISOControl() }) {
            return Pair(NfcUriValidationError.CONTROL_CHARACTER, emptyList())
        }

        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
            ?: return Pair(NfcUriValidationError.MALFORMED, emptyList())

        val scheme = uri.scheme?.lowercase() ?: return Pair(NfcUriValidationError.MISSING_SCHEME, emptyList())
        if (scheme in BLOCKED_SCHEMES) {
            return Pair(NfcUriValidationError.BLOCKED_SCHEME, emptyList())
        }

        if (scheme == "http") {
            warnings.add(NfcUriWarning.CLEARTEXT_HTTP)
        } else if (scheme != "https") {
            warnings.add(NfcUriWarning.CUSTOM_SCHEME)
        }

        val query = uri.query
        if (query != null && (query.contains("token", true) || query.contains("secret", true) || query.contains("key", true))) {
            warnings.add(NfcUriWarning.SENSITIVE_PARAMETERS)
        }

        return Pair(null, warnings)
    }

    /**
     * Constructs a synthetic Tag instance using reflection on hidden Tag.createMockTag
     */
    fun createMockTag(id: ByteArray = byteArrayOf(0x04, 0x12, 0x34, 0x56, 0x78, 0x90.toByte(), 0xAB.toByte())): Tag? {
        return runCatching {
            val techList = intArrayOf(1, 2, 3) // NfcA, Ndef, etc.
            val techExtras = arrayOf(Bundle(), Bundle(), Bundle())
            val createMockTagMethod = Tag::class.java.getDeclaredMethod(
                "createMockTag",
                ByteArray::class.java,
                IntArray::class.java,
                Array<Bundle>::class.java
            ).apply { isAccessible = true }

            createMockTagMethod.invoke(null, id, techList, techExtras) as? Tag
        }.onFailure {
            Log.e(TAG, "Failed to create mock Tag: ${it.message}")
        }.getOrNull()
    }

    /**
     * Dispatches virtual NDEF tag to target application with security checks and fallbacks
     */
    fun dispatchVirtualTag(context: Context, profile: NfcProfile): Result<Boolean> {
        val (error, _) = validateUri(profile.primaryUri)
        if (error != null) {
            return Result.failure(IllegalArgumentException(error.name))
        }

        val uri = Uri.parse(profile.primaryUri)
        val pm = context.packageManager

        // Verify target package if specified
        if (profile.targetPackage.isNotBlank()) {
            val isInstalled = runCatching {
                pm.getPackageInfo(profile.targetPackage, 0)
            }.isSuccess
            if (!isInstalled) {
                return Result.failure(IllegalStateException(NfcDispatchFailureReason.TARGET_NOT_INSTALLED.name))
            }
        }

        val uriRecord = NdefRecord.createUri(uri)
        val records = mutableListOf(uriRecord)
        if (profile.targetPackage.isNotBlank()) {
            records.add(NdefRecord.createApplicationRecord(profile.targetPackage))
        }
        val ndefMessage = NdefMessage(records.toTypedArray())
        val mockTag = createMockTag()

        // 1. Try ACTION_NDEF_DISCOVERED
        val ndefIntent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            data = uri
            if (profile.targetPackage.isNotBlank()) {
                `package` = profile.targetPackage
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(NfcAdapter.EXTRA_TAG, mockTag)
            putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(ndefMessage))
            putExtra(NfcAdapter.EXTRA_ID, mockTag?.id ?: byteArrayOf(0x04, 0x12, 0x34))
        }

        val matches = pm.queryIntentActivities(ndefIntent, 0)
        if (matches.isNotEmpty()) {
            context.startActivity(ndefIntent)
            return Result.success(true)
        }

        // 2. Fallback to ACTION_VIEW if enabled
        if (profile.allowViewFallback) {
            val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                if (profile.targetPackage.isNotBlank()) {
                    `package` = profile.targetPackage
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (pm.queryIntentActivities(viewIntent, 0).isNotEmpty()) {
                context.startActivity(viewIntent)
                return Result.success(true)
            }
        }

        return Result.failure(IllegalStateException(NfcDispatchFailureReason.NO_NDEF_RECEIVER.name))
    }
}

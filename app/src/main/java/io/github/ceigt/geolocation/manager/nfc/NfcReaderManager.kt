package io.github.ceigt.geolocation.manager.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import io.github.ceigt.geolocation.domain.nfc.NfcReadResult

class NfcReaderManager(private val activity: Activity) {

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun isNfcSupported(): Boolean = nfcAdapter != null
    fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true

    fun enableReaderMode(onTagDiscovered: (NfcReadResult) -> Unit) {
        val adapter = nfcAdapter ?: return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        adapter.enableReaderMode(activity, { tag: Tag ->
            val result = readTag(tag)
            if (result != null) {
                activity.runOnUiThread { onTagDiscovered(result) }
            }
        }, flags, Bundle())
    }

    fun disableReaderMode() {
        runCatching { nfcAdapter?.disableReaderMode(activity) }
    }

    private fun readTag(tag: Tag): NfcReadResult? {
        val ndef = Ndef.get(tag) ?: return null
        return runCatching {
            ndef.connect()
            val message: NdefMessage = ndef.ndefMessage ?: return null
            var primaryUri = ""
            var targetPackage: String? = null
            var totalBytes = 0

            for (record in message.records) {
                totalBytes += record.payload.size
                val uri = record.toUri()
                if (uri != null && primaryUri.isEmpty()) {
                    primaryUri = uri.toString()
                }
                // Check if AAR record
                val payloadStr = String(record.payload, Charsets.UTF_8)
                if (String(record.type, Charsets.US_ASCII) == "android.com:pkg") {
                    targetPackage = payloadStr
                }
            }

            val tagIdHex = tag.id.joinToString("") { "%02X".format(it) }
            NfcReadResult(
                primaryUri = primaryUri,
                targetPackage = targetPackage,
                recordCount = message.records.size,
                byteSize = totalBytes,
                tagIdHex = tagIdHex
            )
        }.also {
            runCatching { ndef.close() }
        }.getOrNull()
    }
}

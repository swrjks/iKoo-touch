package com.sudocode.ikoo.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import java.nio.charset.Charset

class IKooNfcManager(
    private val activity: Activity,
    private val onIKooPayloadReceived: (String) -> Unit
) {
    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun isNfcSupported(): Boolean {
        return nfcAdapter != null
    }

    fun isNfcEnabled(): Boolean {
        return nfcAdapter?.isEnabled == true
    }

    fun startReading() {
        val adapter = nfcAdapter ?: return

        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250)
        }

        adapter.enableReaderMode(
            activity,
            { tag -> handleTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NFC_BARCODE,
            options
        )
    }

    fun stopReading() {
        nfcAdapter?.disableReaderMode(activity)
    }

    private fun handleTag(tag: Tag) {
        try {
            val ndef = Ndef.get(tag)

            if (ndef == null) {
                showToast("This NFC tag is not NDEF supported.")
                return
            }

            ndef.connect()

            val message: NdefMessage? = ndef.cachedNdefMessage
            val payload = message
                ?.records
                ?.firstOrNull()
                ?.let { record ->
                    val rawPayload = record.payload

                    if (rawPayload.isEmpty()) return@let null

                    val languageCodeLength = rawPayload[0].toInt() and 0x3F
                    val textStartIndex = 1 + languageCodeLength

                    if (textStartIndex >= rawPayload.size) return@let null

                    String(
                        rawPayload,
                        textStartIndex,
                        rawPayload.size - textStartIndex,
                        Charset.forName("UTF-8")
                    )
                }

            ndef.close()

            if (payload.isNullOrBlank()) {
                showToast("No iKoo data found on NFC.")
                return
            }

            if (payload.startsWith("ikoo://")) {
                activity.runOnUiThread {
                    onIKooPayloadReceived(payload)
                }
            } else {
                showToast("NFC found, but it is not an iKoo action.")
            }
        } catch (e: Exception) {
            Log.e("IKooNfcManager", "NFC read failed", e)
            showToast("Could not read NFC.")
        }
    }

    private fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }
}

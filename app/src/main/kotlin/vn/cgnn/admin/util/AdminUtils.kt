package vn.cgnn.admin.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AdminUtils {
    private val viZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val viFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")

    fun formatViDateTime(iso: String?): String {
        val raw = iso?.trim().orEmpty()
        if (raw.isEmpty()) return ""
        return runCatching {
            val instant = Instant.parse(raw)
            viFormatter.format(instant.atZone(viZone))
        }.getOrElse {
            raw.replace("T", " ").take(16)
        }
    }

    fun copyToClipboard(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun shareExportFile(
        context: Context,
        filename: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, filename)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Lưu hoặc chia sẻ $filename").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    @Suppress("DEPRECATION")
    fun vibrateFeedback(context: Context, success: Boolean) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (success) {
                    VibrationEffect.createOneShot(85, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 120), -1)
                }
                vibrator.vibrate(effect)
            } else {
                vibrator.vibrate(if (success) 85L else 200L)
            }
        }
    }
}

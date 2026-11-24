package vn.cgnn.admin.data.network

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.text.Normalizer

object AdminAuthUtils {
    private val json = Json { ignoreUnknownKeys = true }

    private val adminRoleNames = setOf(
        "admin", "administrator", "staff", "manager", "superadmin", "super_admin", "super admin",
    )
    private val technicianRoleNames = setOf(
        "technician", "technical", "tech", "ky_thuat", "ky-thuat", "ky thuat",
    )

    private fun normalizeRole(value: String?): String {
        val raw = value.orEmpty()
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .trim()
            .lowercase()
            .replace(Regex("[_-]+"), " ")
            .replace(Regex("\\s+"), " ")
        return decomposed
    }

    fun isMachineCareStaffRole(role: String?): Boolean {
        val normalized = normalizeRole(role)
        if (normalized.isEmpty()) return false
        val aliases = setOf(
            normalized,
            normalized.replace(" ", "_"),
            normalized.replace(" ", "-"),
        )
        return aliases.any { it in adminRoleNames || it in technicianRoleNames }
    }

    data class TokenPayload(
        val role: String,
        val expSeconds: Long,
        val email: String,
    )

    fun decodePayload(token: String?): TokenPayload? {
        if (token.isNullOrBlank()) return null
        return runCatching {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val obj: JsonObject = json.parseToJsonElement(String(payloadBytes, Charsets.UTF_8)).jsonObject
            TokenPayload(
                role = obj["role"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                expSeconds = obj["exp"]?.jsonPrimitive?.longOrNull ?: 0L,
                email = obj["sub"]?.jsonPrimitive?.contentOrNull
                    ?: obj["email"]?.jsonPrimitive?.contentOrNull
                    ?: "",
            )
        }.getOrNull()
    }

    fun isUsableAdminToken(token: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        val payload = decodePayload(token) ?: return false
        if (payload.expSeconds <= 0L || nowMs >= payload.expSeconds * 1000L) return false
        return isMachineCareStaffRole(payload.role)
    }
}

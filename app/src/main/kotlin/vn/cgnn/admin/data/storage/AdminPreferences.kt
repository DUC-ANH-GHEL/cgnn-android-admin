package vn.cgnn.admin.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.security.SecureRandom

@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

@Serializable
data class NfcListPrefs(
    val search: String = "",
    val status: String = "",
    val orderStatus: String = "",
    val selectedTagIds: List<Long> = emptyList(),
    val deselectAfterProgramming: Boolean = true,
    val deselectAfterPrinting: Boolean = true,
    val showProgramButton: Boolean = true,
    val showGuideButton: Boolean = true,
)

class AdminPreferences(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private val securePrefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cgnn_admin_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        context.getSharedPreferences("cgnn_admin_secure_fallback", Context.MODE_PRIVATE)
    }

    private val uiPrefs: SharedPreferences =
        context.getSharedPreferences("cgnn_admin_nfc_ui_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var cachedToken: String? = securePrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getAccessToken(): String? = cachedToken?.takeIf { it.isNotBlank() }

    fun saveAccessToken(token: String) {
        val normalized = token.trim()
        cachedToken = normalized
        securePrefs.edit()
            .putString(KEY_ACCESS_TOKEN, normalized)
            .putBoolean(KEY_LOGGED_OUT, false)
            .apply()
    }

    fun clearAccessToken() {
        cachedToken = null
        securePrefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    fun isLoggedOut(): Boolean = securePrefs.getBoolean(KEY_LOGGED_OUT, false)

    fun setLoggedOut(loggedOut: Boolean) {
        securePrefs.edit().putBoolean(KEY_LOGGED_OUT, loggedOut).apply()
    }

    fun getSavedEmail(): String = uiPrefs.getString(KEY_SAVED_EMAIL, "") ?: ""

    fun saveEmail(email: String) {
        uiPrefs.edit().putString(KEY_SAVED_EMAIL, email.trim()).apply()
    }

    fun getOrCreateRefreshRetryKey(): String {
        val existing = securePrefs.getString(KEY_REFRESH_RETRY, null)
        if (existing != null && existing.matches(Regex("^[a-f0-9]{64}$"))) return existing
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val created = bytes.joinToString("") { "%02x".format(it) }
        securePrefs.edit().putString(KEY_REFRESH_RETRY, created).apply()
        return created
    }

    fun clearRefreshRetryKey() {
        securePrefs.edit().remove(KEY_REFRESH_RETRY).apply()
    }

    fun loadListPrefs(): NfcListPrefs {
        val raw = uiPrefs.getString(KEY_LIST_PREFS, null) ?: return NfcListPrefs()
        return runCatching { json.decodeFromString<NfcListPrefs>(raw) }.getOrDefault(NfcListPrefs())
    }

    fun saveListPrefs(prefs: NfcListPrefs) {
        runCatching {
            uiPrefs.edit().putString(KEY_LIST_PREFS, json.encodeToString(prefs)).apply()
        }
    }

    val cookieJar: CookieJar = object : CookieJar {
        private val lock = Any()
        private var cookies: MutableList<StoredCookie> = loadCookies()

        private fun loadCookies(): MutableList<StoredCookie> {
            val raw = securePrefs.getString(KEY_COOKIES, null) ?: return mutableListOf()
            return runCatching { json.decodeFromString<List<StoredCookie>>(raw).toMutableList() }
                .getOrElse { mutableListOf() }
        }

        private fun persistLocked() {
            val now = System.currentTimeMillis()
            cookies.removeAll { it.expiresAt <= now }
            runCatching {
                securePrefs.edit().putString(KEY_COOKIES, json.encodeToString(cookies)).apply()
            }
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            synchronized(lock) {
                for (cookie in cookies) {
                    this.cookies.removeAll {
                        it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
                    }
                    if (cookie.expiresAt > System.currentTimeMillis() && cookie.value.isNotEmpty()) {
                        this.cookies.add(
                            StoredCookie(
                                name = cookie.name,
                                value = cookie.value,
                                expiresAt = cookie.expiresAt,
                                domain = cookie.domain,
                                path = cookie.path,
                                secure = cookie.secure,
                                httpOnly = cookie.httpOnly,
                                hostOnly = cookie.hostOnly,
                            ),
                        )
                    }
                }
                persistLocked()
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                val valid = cookies.filter { it.expiresAt > now }
                if (valid.size != cookies.size) {
                    cookies = valid.toMutableList()
                    persistLocked()
                }
                return valid.mapNotNull { sc ->
                    val builder = Cookie.Builder()
                        .name(sc.name)
                        .value(sc.value)
                        .expiresAt(sc.expiresAt)
                        .path(sc.path)
                    if (sc.hostOnly) builder.hostOnlyDomain(sc.domain) else builder.domain(sc.domain)
                    if (sc.secure) builder.secure()
                    if (sc.httpOnly) builder.httpOnly()
                    val built = builder.build()
                    if (built.matches(url)) built else null
                }
            }
        }
    }

    fun clearCookies() {
        securePrefs.edit().remove(KEY_COOKIES).apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_LOGGED_OUT = "logged_out"
        private const val KEY_REFRESH_RETRY = "refresh_retry_key"
        private const val KEY_COOKIES = "cookies_json"
        private const val KEY_SAVED_EMAIL = "saved_email"
        private const val KEY_LIST_PREFS = "nfc_list_prefs_v1"
    }
}

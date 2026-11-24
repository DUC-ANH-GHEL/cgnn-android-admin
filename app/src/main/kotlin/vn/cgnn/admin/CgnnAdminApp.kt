package vn.cgnn.admin

import android.app.Application
import vn.cgnn.admin.data.network.AdminApiClient
import vn.cgnn.admin.data.storage.AdminPreferences

class CgnnAdminApp : Application() {
    lateinit var preferences: AdminPreferences
        private set

    lateinit var apiClient: AdminApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AdminPreferences(this)
        apiClient = AdminApiClient(preferences)
    }
}

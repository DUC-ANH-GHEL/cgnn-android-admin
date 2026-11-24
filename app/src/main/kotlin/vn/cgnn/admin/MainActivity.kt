package vn.cgnn.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.cgnn.admin.ui.CgnnAdminRoot
import vn.cgnn.admin.ui.NfcTagsViewModel
import vn.cgnn.admin.ui.theme.CgnnAdminTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CgnnAdminTheme {
                val vm: NfcTagsViewModel = viewModel()
                CgnnAdminRoot(vm)
            }
        }
    }
}

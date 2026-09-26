package io.github.p1neapplexpress.openflux.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import io.github.p1neapplexpress.openflux.R
import io.github.p1neapplexpress.openflux.event.EventBus
import io.github.p1neapplexpress.openflux.event.AppEvent
import io.github.p1neapplexpress.openflux.update.AppUpdater
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.main, MainFragment(), "")
            .commit()

        AppUpdater.checkOnLaunch(this)


        lifecycleScope.launch {
            EventBus.events.collect { ev ->
                if (ev is AppEvent.TransportConnected) AppUpdater.checkOnLaunch(this@MainActivity)
                supportFragmentManager.fragments.forEach { f ->
                    if (f is BaseFragment) f.onNewEvent(ev)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdater.resumeInstall(this)
    }

}

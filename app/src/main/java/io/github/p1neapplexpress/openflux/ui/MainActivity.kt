package io.github.p1neapplexpress.openflux.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
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
import io.github.p1neapplexpress.openflux.service.YandexCaptchaFiles
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val tunnelsVM: TunnelsViewModel by viewModels()
    private var captchaSnackbar: Snackbar? = null
    private var lastCaptchaPrompt = 0L

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
                if (ev is AppEvent.CaptchaRequired) showCaptchaIfNeeded()
                supportFragmentManager.fragments.forEach { f ->
                    if (f is BaseFragment) f.onNewEvent(ev)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppUpdater.resumeInstall(this)
        showCaptchaIfNeeded()
    }

    private fun showCaptchaIfNeeded() {
        val activeTransport = tunnelsVM.active.value.tunnel?.transportType
        val selectedTransport = tunnelsVM.selected.value?.transportType
        if ((activeTransport !in setOf("yandex", "vyandex") &&
            selectedTransport !in setOf("yandex", "vyandex")) ||
            YandexCaptchaFiles.pendingURL(this) == null) {
            captchaSnackbar?.dismiss()
            captchaSnackbar = null
            return
        }
        if (captchaSnackbar?.isShown != true) {
            captchaSnackbar = Snackbar.make(findViewById(R.id.main), R.string.captcha_title,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.captcha_open) { openCaptcha() }
                .also { it.show() }
        }
        val modified = YandexCaptchaFiles.challengeFile(this).lastModified()
        if (modified <= lastCaptchaPrompt || isFinishing || isDestroyed) return
        lastCaptchaPrompt = modified
        AlertDialog.Builder(this)
            .setTitle(R.string.captcha_title)
            .setMessage(R.string.captcha_message)
            .setNegativeButton(R.string.captcha_later, null)
            .setPositiveButton(R.string.captcha_open) { _, _ -> openCaptcha() }
            .show()
    }

    private fun openCaptcha() {
        if (YandexCaptchaFiles.pendingURL(this) != null) {
            startActivity(Intent(this, YandexCaptchaActivity::class.java))
        }
    }

}

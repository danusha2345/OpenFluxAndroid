package io.github.p1neapplexpress.openflux.update

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import io.github.p1neapplexpress.openflux.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Checks our GitHub release, then hands a verified APK to Android's installer. */
object AppUpdater {
    private const val releaseURL =
        "https://api.github.com/repos/danusha2345/OpenFluxAndroid/releases/latest"
    private const val checkInterval = 24 * 60 * 60 * 1000L
    private var checking = false
    private var pendingInstall: File? = null
    private var offeredVersionCode: Long? = null

    fun resumeInstall(activity: AppCompatActivity) {
        val apk = pendingInstall ?: return
        if (activity.packageManager.canRequestPackageInstalls()) {
            pendingInstall = null
            openInstaller(activity, apk)
        }
    }

    fun checkOnLaunch(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences("app_updates", 0)
        if (checking || System.currentTimeMillis() - prefs.getLong("last_check", 0L) < checkInterval) return
        checking = true

        activity.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val connection = open(releaseURL)
                        try {
                            if (connection.responseCode == 404) return@runCatching null
                            require(connection.responseCode == 200)
                            val body = connection.inputStream.use { input ->
                                val output = ByteArrayOutputStream()
                                val buffer = ByteArray(16 * 1024)
                                while (output.size() <= 1_000_000) {
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                }
                                output.toByteArray()
                            }
                            require(body.size <= 1_000_000)
                            ReleaseParser.findUpdate(String(body, Charsets.UTF_8), BuildConfig.VERSION_CODE.toLong())
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
                if (result.isSuccess && result.getOrNull() == null) {
                    prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
                }
                val update = result.getOrNull()
                if (update != null && offeredVersionCode != update.versionCode &&
                    !activity.isFinishing && !activity.isDestroyed) {
                    offeredVersionCode = update.versionCode
                    AlertDialog.Builder(activity)
                        .setTitle("Обновление OpenFlux")
                        .setMessage("Доступна версия ${update.version}. Скачать и установить?")
                        .setNegativeButton("Позже", null)
                        .setPositiveButton("Обновить") { _, _ -> downloadAndInstall(activity, update) }
                        .show()
                }
            } finally {
                checking = false
            }
        }
    }

    private fun downloadAndInstall(activity: AppCompatActivity, update: AvailableUpdate) {
        val progress = AlertDialog.Builder(activity)
            .setMessage("Загрузка и проверка обновления…")
            .setCancelable(false)
            .create()
        progress.show()
        activity.lifecycleScope.launch {
            val apk = withContext(Dispatchers.IO) {
                runCatching { downloadVerified(activity, update) }.getOrNull()
            }
            progress.dismiss()
            if (activity.isFinishing || activity.isDestroyed) return@launch
            if (apk == null) {
                AlertDialog.Builder(activity)
                    .setMessage("Не удалось проверить обновление. Попробуйте позже.")
                    .setNegativeButton("Закрыть", null)
                    .setPositiveButton("Повторить") { _, _ -> downloadAndInstall(activity, update) }
                    .show()
                return@launch
            }
            if (!activity.packageManager.canRequestPackageInstalls()) {
                pendingInstall = apk
                AlertDialog.Builder(activity)
                    .setMessage("Разрешите установку обновлений для OpenFlux в настройках Android.")
                    .setNegativeButton("Позже", null)
                    .setPositiveButton("Открыть настройки") { _, _ ->
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")))
                    }
                    .show()
                return@launch
            }
            openInstaller(activity, apk)
        }
    }

    private fun openInstaller(activity: AppCompatActivity, apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activity.startActivity(intent) }.onFailure {
            AlertDialog.Builder(activity)
                .setMessage("Android не смог открыть установщик APK.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun downloadVerified(activity: AppCompatActivity, update: AvailableUpdate): File {
        val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
        val pending = File(dir, "OpenFluxAndroid.apk.part")
        val apk = File(dir, "OpenFluxAndroid.apk")
        pending.delete()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val connection = open(update.asset.browser_download_url)
            val count: Long
            try {
                require(connection.responseCode == 200)
                count = connection.inputStream.use { input ->
                    pending.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= update.asset.size)
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                        }
                        total
                    }
                }
            } finally {
                connection.disconnect()
            }
            require(count == update.asset.size)
            require(digest.digest().joinToString("") { "%02x".format(it) } ==
                update.asset.digest!!.removePrefix("sha256:").lowercase())
            require(samePackageAndSigner(activity, pending, update.versionCode))
            if (apk.exists()) require(apk.delete())
            require(pending.renameTo(apk))
            return apk
        } finally {
            pending.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun samePackageAndSigner(activity: AppCompatActivity, apk: File, versionCode: Long): Boolean {
        val pm = activity.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        val installed = pm.getPackageInfo(activity.packageName, flags)
        val candidateSigner = candidate.signerBytes()
        val installedSigner = installed.signerBytes()
        return candidate.packageName == activity.packageName &&
            candidate.versionCodeLong() == versionCode &&
            candidateSigner.isNotEmpty() && candidateSigner.contentEquals(installedSigner)
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeLong(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerBytes(): ByteArray {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            signingInfo?.apkContentsSigners
        } else {
            signatures
        } ?: return byteArrayOf()
        return signatures.flatMap { it.toByteArray().asIterable() }.toByteArray()
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OpenFluxAndroid-updater")
        }
}

package io.github.p1neapplexpress.openflux.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ReleaseInfo(
    val tag_name: String,
    val assets: List<ReleaseAsset>,
)

@Serializable
internal data class ReleaseAsset(
    val name: String,
    val browser_download_url: String,
    val digest: String? = null,
    val size: Long,
)

internal data class AvailableUpdate(val version: String, val versionCode: Long, val asset: ReleaseAsset)

internal object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val versionPattern = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
    private const val assetName = "OpenFluxAndroid.apk"
    private const val downloadPrefix =
        "https://github.com/danusha2345/OpenFluxAndroid/releases/download/"

    fun findUpdate(body: String, installedVersionCode: Long): AvailableUpdate? {
        val release = json.decodeFromString<ReleaseInfo>(body)
        val match = versionPattern.matchEntire(release.tag_name) ?: return null
        val (major, minor, patch) = match.destructured
        val versionCode = major.toLong() * 10000 + minor.toLong() * 100 + patch.toLong()
        if (versionCode <= installedVersionCode || minor.toInt() >= 100 || patch.toInt() >= 100) {
            return null
        }
        val asset = release.assets.singleOrNull {
            it.name == assetName &&
                it.browser_download_url.startsWith(downloadPrefix) &&
                it.digest?.matches(Regex("^sha256:[0-9a-fA-F]{64}$")) == true &&
                it.size in 1..150_000_000
        } ?: return null
        return AvailableUpdate(release.tag_name, versionCode, asset)
    }
}

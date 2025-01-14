package earth.worldwind.layer

import earth.worldwind.WorldWind
import earth.worldwind.geom.Angle
import earth.worldwind.geom.Sector
import earth.worldwind.render.image.ImageDecoder
import kotlinx.coroutines.*

actual abstract class TiledImageLayer actual constructor(name: String): AbstractTiledImageLayer(name) {
    /**
     * Start a new coroutine Job that downloads all imagery for a given sector and resolution,
     * without downloading imagery that is already in the cache.
     *
     * Note that the target resolution must be provided in radians of latitude per texel, which is the resolution in
     * meters divided by the globe radius.
     *
     * @param sector     the sector to download data for.
     * @param resolution the desired resolution in angular value of latitude per pixel.
     * @param cache      render resource cache to access absent resource list
     * @param scope      custom coroutine scope for better job management. Global scope by default.
     * @param onProgress an optional retrieval listener, indication successful, failed and total tiles amount.
     *
     * @return the coroutine Job executing the retrieval or `null` if the specified sector does
     * not intersect the layer bounding sector.
     *
     * @throws IllegalStateException if tiled surface image is not initialized or cache is not configured.
     * @throws IllegalArgumentException if sector does not intersect tiled surface image sector
     */
    @OptIn(DelicateCoroutinesApi::class)
    @Throws(IllegalStateException::class, IllegalArgumentException::class)
    fun makeLocal(
        sector: Sector, resolution: Angle, scope: CoroutineScope = GlobalScope,
        onProgress: ((Int, Int, Int) -> Unit)? = null
    ): Job {
        val imageDecoder = ImageDecoder()
        return launchBulkRetrieval(scope, sector, resolution, onProgress) { imageSource, cacheSource, options ->
            // Check if tile exists in cache. If cache retrieval fail, then image source will be requested.
            // TODO If retrieved cache source is outdated, then retrieve original image source to refresh cache
            imageDecoder.run { decodeImage(cacheSource, options) ?: decodeImage(imageSource, options) }?.let {
                // Un-mark cache source from absent list
                WorldWind.unmarkResourceAbsent(cacheSource.hashCode())
                true
            } ?: false
        }
    }
}
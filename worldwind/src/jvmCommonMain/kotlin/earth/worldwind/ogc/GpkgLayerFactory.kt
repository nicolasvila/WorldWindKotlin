package earth.worldwind.ogc

import earth.worldwind.layer.RenderableLayer
import earth.worldwind.ogc.gpkg.GeoPackage
import earth.worldwind.shape.TiledSurfaceImage
import earth.worldwind.util.LevelSet
import earth.worldwind.util.Logger.WARN
import earth.worldwind.util.Logger.logMessage
import earth.worldwind.util.Logger.makeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GpkgLayerFactory {

    suspend fun createLayer(pathName: String, layerNames: List<String> = emptyList()): RenderableLayer {
        val layer = createGeoPackageLayer(pathName, layerNames)
        require(!layer.isEmpty()) {
            makeMessage("GpkgLayerFactory", "createLayer", "Unsupported GeoPackage contents")
        }
        return layer
    }

    private suspend fun createGeoPackageLayer(pathName: String, layerNames: List<String>) = withContext(Dispatchers.IO) {
        RenderableLayer().apply {
            val geoPackage = GeoPackage(pathName)
            for (content in geoPackage.content) if (layerNames.isEmpty() || layerNames.contains(content.tableName)) {
                try {
                    val config = geoPackage.buildLevelSetConfig(content)
                    val surfaceImage = TiledSurfaceImage(GpkgTileFactory(content), LevelSet(config)).apply {
                        displayName = content.identifier
                    }
                    addRenderable(surfaceImage)
                } catch (e: IllegalArgumentException) {
                    logMessage(WARN, "GpkgLayerFactory", "createGeoPackageLayer", e.message!!)
                }
            }
        }
    }
}
package earth.worldwind.layer.atmosphere

import earth.worldwind.draw.DrawContext
import earth.worldwind.render.Texture
import earth.worldwind.util.kgl.*
import kotlin.math.*

/**
 * Precomputed 2D optical-depth LUT based on Bruneton's transmittance model.
 *
 * Replaces the O'Neil polynomial scaleFunc approximation which diverges for
 * near-horizontal rays (cos -> 0), causing dark mountains and bright horizon
 * artifacts from low orbit.
 *
 * Texture size: 256 x 64, RGBA8
 * - R channel: normalized optical depth (tau / TAU_MAX, linear encoding)
 * - UV parameterization:
 *   U = (mu + 1) / 2  where mu = cos(zenith angle), range [-1, 1]
 *   V = (r - globeRadius) / atmosphereThickness, range [0, 1]
 *
 * In the GLSL shader, decode with: tau = texture.r * 8.0
 *
 * For mu < 0 (ray hits Earth): encodes TAU_MAX (fully opaque, night side).
 * For mu >= 0 (ray to atmosphere top): numerically integrated Chapman function.
 */
class TransmittanceLUT(width: Int = 256, height: Int = 64) : Texture(width, height, GL_RGBA, GL_UNSIGNED_BYTE) {

    /** Maximum optical depth representable; values above this are fully dark */
    private val tauMax = 8.0

    var globeRadius = 6.371e6
    var atmosphereRadius = 6.531e6
    var scaleDepth = 0.25

    init {
        setTexParameter(GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        setTexParameter(GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
    }

    override fun allocTexImage(dc: DrawContext) {
        val data = computeLUT()
        dc.gl.texImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, format, type, data)
    }

    private fun computeLUT(): ByteArray {
        val data = ByteArray(width * height * 4)
        val atmosphereThickness = atmosphereRadius - globeRadius
        // Rayleigh scale height - matches O'Neil's scaleDepth parameter
        val H = scaleDepth * atmosphereThickness

        for (y in 0 until height) {
            val r = globeRadius + (y.toDouble() / (height - 1)) * atmosphereThickness
            for (x in 0 until width) {
                val mu = x.toDouble() / (width - 1) * 2.0 - 1.0  // [-1.0, 1.0]
                val tau = computeOpticalDepth(r, mu, H, atmosphereThickness)
                val encoded = (tau / tauMax).coerceIn(0.0, 1.0)
                val byte = (encoded * 255.0 + 0.5).toInt().coerceIn(0, 255).toByte()
                val idx = (y * width + x) * 4
                data[idx + 0] = byte  // R: normalized optical depth
                data[idx + 1] = 0
                data[idx + 2] = 0
                data[idx + 3] = -1   // A: 255 = fully opaque
            }
        }
        return data
    }

    /**
     * Numerically integrates the Chapman function for a ray starting at radius [r]
     * toward direction [mu] (cosine of zenith angle), using 50 integration steps.
     * Returns optical depth normalized by atmosphereThickness.
     */
    private fun computeOpticalDepth(r: Double, mu: Double, H: Double, atmosphereThickness: Double): Double {
        // For downward rays, check if they intersect the Earth's surface
        if (mu < 0.0) {
            val discriminantEarth = r * r * (mu * mu - 1.0) + globeRadius * globeRadius
            if (discriminantEarth >= 0.0) {
                // Ray hits Earth before reaching atmosphere top.
                // Return maximum tau to ensure night-side darkness.
                return tauMax * atmosphereThickness
            }
            // Ray arcs below horizon but clears Earth - fall through to normal computation.
            // This handles slightly-below-horizon angles near the atmosphere limb.
        }

        val sMax = rayLengthToAtmosphereTop(r, mu)
        if (sMax <= 0.0) return 0.0

        // Numerical integration: 50 steps (accuracy vs performance tradeoff)
        val N = 50
        val ds = sMax / N
        var sum = 0.0
        for (i in 0 until N) {
            val s = (i + 0.5) * ds
            val rS = sqrt(r * r + 2.0 * r * mu * s + s * s)
            val altitude = max(rS - globeRadius, 0.0)
            sum += exp(-altitude / H)
        }
        // Normalize by atmosphere thickness to match O'Neil's scaleFunc units
        return sum * ds / atmosphereThickness
    }

    /** Distance from altitude [r] toward direction [mu] to the atmosphere top sphere. */
    private fun rayLengthToAtmosphereTop(r: Double, mu: Double): Double {
        val discriminant = r * r * (mu * mu - 1.0) + atmosphereRadius * atmosphereRadius
        if (discriminant < 0.0) return 0.0
        return max(0.0, -r * mu + sqrt(discriminant))
    }
}


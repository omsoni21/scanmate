package com.example.domain.model

data class BatteryStatus(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false
) {
    val isLowBattery: Boolean
        get() = levelPercent in 0 until LOW_BATTERY_THRESHOLD_PERCENT

    companion object {
        const val LOW_BATTERY_THRESHOLD_PERCENT = 20
    }
}

data class BatterySaverProfile(
    val isActive: Boolean = false,
    val isLowBattery: Boolean = false,
    val batteryPercent: Int = 100,
    val autoEnabled: Boolean = true,
    val alwaysOnEnabled: Boolean = false,
    val scanQuality: ImageQuality = ImageQuality.BALANCED,
    val maxScanDimensionPx: Int = BatterySaverResolver.NORMAL_MAX_SCAN_DIM_PX,
    val maxCaptureInputDimensionPx: Int = BatterySaverResolver.NORMAL_CAPTURE_INPUT_DIM_PX,
    val maxPreviewDimensionPx: Int = BatterySaverResolver.NORMAL_PREVIEW_DIM_PX,
    val enhancementIntensity: Float = BatterySaverResolver.NORMAL_ENHANCEMENT_INTENSITY,
    val defaultSharpness: Float = BatterySaverResolver.NORMAL_SHARPNESS,
    val analysisThrottleMs: Long = 0L
)

object BatterySaverResolver {
    const val NORMAL_MAX_SCAN_DIM_PX = 2000
    const val BATTERY_SAVER_MAX_SCAN_DIM_PX = 1080
    const val NORMAL_CAPTURE_INPUT_DIM_PX = 2200
    const val BATTERY_SAVER_CAPTURE_INPUT_DIM_PX = 1120
    const val NORMAL_PREVIEW_DIM_PX = 1400
    const val BATTERY_SAVER_PREVIEW_DIM_PX = 900
    const val NORMAL_ENHANCEMENT_INTENSITY = 1.0f
    const val BATTERY_SAVER_ENHANCEMENT_INTENSITY = 0.45f
    const val NORMAL_SHARPNESS = 0.25f
    const val BATTERY_SAVER_SHARPNESS = 0.08f
    const val BATTERY_SAVER_ANALYSIS_THROTTLE_MS = 350L

    fun resolve(
        batteryStatus: BatteryStatus,
        autoEnabled: Boolean,
        alwaysOnEnabled: Boolean
    ): BatterySaverProfile {
        val isLow = batteryStatus.isLowBattery
        val active = alwaysOnEnabled || (autoEnabled && isLow)
        return if (active) {
            BatterySaverProfile(
                isActive = true,
                isLowBattery = isLow,
                batteryPercent = batteryStatus.levelPercent,
                autoEnabled = autoEnabled,
                alwaysOnEnabled = alwaysOnEnabled,
                scanQuality = ImageQuality.COMPACT,
                maxScanDimensionPx = BATTERY_SAVER_MAX_SCAN_DIM_PX,
                maxCaptureInputDimensionPx = BATTERY_SAVER_CAPTURE_INPUT_DIM_PX,
                maxPreviewDimensionPx = BATTERY_SAVER_PREVIEW_DIM_PX,
                enhancementIntensity = BATTERY_SAVER_ENHANCEMENT_INTENSITY,
                defaultSharpness = BATTERY_SAVER_SHARPNESS,
                analysisThrottleMs = BATTERY_SAVER_ANALYSIS_THROTTLE_MS
            )
        } else {
            BatterySaverProfile(
                isActive = false,
                isLowBattery = isLow,
                batteryPercent = batteryStatus.levelPercent,
                autoEnabled = autoEnabled,
                alwaysOnEnabled = alwaysOnEnabled,
                scanQuality = ImageQuality.BALANCED,
                maxScanDimensionPx = NORMAL_MAX_SCAN_DIM_PX,
                maxCaptureInputDimensionPx = NORMAL_CAPTURE_INPUT_DIM_PX,
                maxPreviewDimensionPx = NORMAL_PREVIEW_DIM_PX,
                enhancementIntensity = NORMAL_ENHANCEMENT_INTENSITY,
                defaultSharpness = NORMAL_SHARPNESS,
                analysisThrottleMs = 0L
            )
        }
    }
}

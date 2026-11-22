package com.example.data.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.example.data.local.PreferencesManager
import com.example.domain.model.BatterySaverProfile
import com.example.domain.model.BatterySaverResolver
import com.example.domain.model.BatteryStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class BatteryStatusMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    val batteryStatus: Flow<BatteryStatus> = callbackFlow {
        trySend(readCurrentBatteryStatus())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                parseBatteryIntent(intent)?.let { trySend(it) }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.distinctUntilChanged()

    val batterySaverProfile: Flow<BatterySaverProfile> = combine(
        batteryStatus,
        preferencesManager.batterySaverAutoEnabled,
        preferencesManager.batterySaverAlwaysOn
    ) { status, autoEnabled, alwaysOn ->
        BatterySaverResolver.resolve(
            batteryStatus = status,
            autoEnabled = autoEnabled,
            alwaysOnEnabled = alwaysOn
        )
    }

    suspend fun currentProfile(): BatterySaverProfile = batterySaverProfile.first()

    fun readCurrentBatteryStatus(): BatteryStatus {
        val stickyIntent = runCatching {
            ContextCompat.registerReceiver(
                context,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
        }.getOrNull()

        parseBatteryIntent(stickyIntent)?.let { return it }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val safePercent = if (capacity in 0..100) capacity else 100
        val isCharging = bm?.isCharging ?: false
        return BatteryStatus(levelPercent = safePercent, isCharging = isCharging)
    }

    companion object {
        fun parseBatteryIntent(intent: Intent?): BatteryStatus? {
            intent ?: return null
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level < 0 || scale <= 0) return null

            val percent = ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            return BatteryStatus(levelPercent = percent, isCharging = charging)
        }
    }
}

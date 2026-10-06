package com.thermalsentinel.engine.domain

/**
 * Charging state as reported in `BatteryManager.EXTRA_STATUS`.
 *
 * Platform constants (API 1): UNKNOWN=1, CHARGING=2, DISCHARGING=3,
 * NOT_CHARGING=4, FULL=5. Values are duplicated here as documented literals so
 * this layer stays free of `android.*` and can be unit-tested on the JVM.
 */
enum class ChargingStatus(
    val storageValue: String,
    val label: String,
    /** True only when energy is actively flowing into the battery. */
    val activelyCharging: Boolean
) {
    CHARGING("charging", "Charging", true),
    DISCHARGING("discharging", "Discharging", false),
    NOT_CHARGING("not_charging", "Not charging", false),
    FULL("full", "Full", false),
    UNKNOWN("unknown", "Unknown", false);

    companion object {
        fun fromStorage(value: String?): ChargingStatus =
            entries.firstOrNull { it.storageValue == value } ?: UNKNOWN

        fun fromPlatformStatus(status: Int?): ChargingStatus = when (status) {
            2 -> CHARGING
            3 -> DISCHARGING
            4 -> NOT_CHARGING
            5 -> FULL
            else -> UNKNOWN
        }
    }
}

/**
 * External power source from `BatteryManager.EXTRA_PLUGGED`.
 *
 * Platform constants (API 1/17/33): AC=1, USB=2, WIRELESS=4, DOCK=8;
 * 0 means "on battery".
 */
enum class PowerSource(
    val storageValue: String,
    val label: String,
    val isExternalPower: Boolean
) {
    NONE("none", "Battery", false),
    AC("ac", "AC charger", true),
    USB("usb", "USB", true),
    WIRELESS("wireless", "Wireless", true),
    DOCK("dock", "Dock", true),
    UNKNOWN("unknown", "Unknown", false);

    companion object {
        fun fromStorage(value: String?): PowerSource =
            entries.firstOrNull { it.storageValue == value } ?: UNKNOWN

        /**
         * `EXTRA_PLUGGED` is documented as a bit-set. Devices in practice send
         * a single value, but combinations are possible, so bits are tested from
         * the fastest charge path downwards.
         */
        fun fromPluginBits(bits: Int?): PowerSource {
            if (bits == null) return UNKNOWN
            if (bits == 0) return NONE
            if (bits and 1 != 0) return AC
            if (bits and 2 != 0) return USB
            if (bits and 4 != 0) return WIRELESS
            if (bits and 8 != 0) return DOCK
            return UNKNOWN
        }
    }
}

/**
 * Battery health from `BatteryManager.EXTRA_HEALTH`.
 *
 * Platform constants: UNKNOWN=1, GOOD=2, OVERHEAT=3, DEAD=4, OVER_VOLTAGE=5,
 * UNSPECIFIED_FAILURE=6, COLD=7 (COLD is API 11+).
 */
enum class BatteryHealth(
    val storageValue: String,
    val label: String,
    /** True when the platform is flagging a condition worth surfacing. */
    val isAdverse: Boolean
) {
    GOOD("good", "Good", false),
    OVERHEAT("overheat", "Overheat", true),
    COLD("cold", "Cold", true),
    DEAD("dead", "Dead", true),
    OVER_VOLTAGE("over_voltage", "Over voltage", true),
    UNSPECIFIED_FAILURE("unspecified_failure", "Unspecified failure", true),
    UNKNOWN("unknown", "Unknown", false);

    companion object {
        fun fromStorage(value: String?): BatteryHealth =
            entries.firstOrNull { it.storageValue == value } ?: UNKNOWN

        fun fromPlatformHealth(health: Int?): BatteryHealth = when (health) {
            2 -> GOOD
            3 -> OVERHEAT
            4 -> DEAD
            5 -> OVER_VOLTAGE
            6 -> UNSPECIFIED_FAILURE
            7 -> COLD
            else -> UNKNOWN
        }
    }
}

/**
 * `BatteryManager.EXTRA_CAPACITY_LEVEL` (API 36+). Unlike a raw percentage this
 * is the platform's own statement about whether background work is advisable.
 *
 * Platform constants: UNSUPPORTED=-1, UNKNOWN=0, CRITICAL=1, LOW=2, NORMAL=3,
 * HIGH=4, FULL=5.
 */
enum class BatteryCapacityLevel(
    val storageValue: String,
    val label: String,
    val platformValue: Int
) {
    UNSUPPORTED("unsupported", "Unsupported", -1),
    UNKNOWN("unknown", "Unknown", 0),
    CRITICAL("critical", "Critical", 1),
    LOW("low", "Low", 2),
    NORMAL("normal", "Normal", 3),
    HIGH("high", "High", 4),
    FULL("full", "Full", 5);

    companion object {
        fun fromStorage(value: String?): BatteryCapacityLevel =
            entries.firstOrNull { it.storageValue == value } ?: UNKNOWN

        fun fromPlatformValue(value: Int?): BatteryCapacityLevel =
            entries.firstOrNull { it.platformValue == value } ?: UNKNOWN
    }
}

/**
 * One consistent snapshot of everything the battery APIs reported.
 *
 * [observedAtMillis] is the time the underlying battery broadcast was received,
 * which can be older than the sample that carries it. The UI uses the
 * difference to say "reading from 3 minutes ago" instead of implying freshness.
 *
 * Naming rule: [temperatureC] is the **battery** temperature. It is never
 * labelled "device temperature" because the two are not the same measurement.
 */
data class BatterySnapshot(
    val observedAtMillis: Long,
    val batteryPercent: Reading<Int>,
    val temperatureC: Reading<Float>,
    val voltageMilliVolts: Reading<Int>,
    val currentMicroAmps: Reading<Int>,
    val averageCurrentMicroAmps: Reading<Int>,
    val chargeCounterMicroAmpHours: Reading<Int>,
    val energyCounterNanoWattHours: Reading<Long>,
    val chargingStatus: Reading<ChargingStatus>,
    val plugged: Reading<PowerSource>,
    val health: Reading<BatteryHealth>,
    val capacityLevel: Reading<BatteryCapacityLevel>,
    val cycleCount: Reading<Int>,
    val technology: Reading<String>,
    val present: Reading<Boolean>
) {
    /**
     * True when the device is on external power. Either signal is enough:
     * `EXTRA_PLUGGED` is the more reliable of the two on some OEM builds, and
     * `EXTRA_STATUS` is the more reliable one on others.
     */
    val onExternalPower: Boolean
        get() = plugged.valueOrNull?.isExternalPower == true ||
            chargingStatus.valueOrNull?.activelyCharging == true

    companion object {
        fun notCollectedYet(nowMillis: Long): BatterySnapshot = BatterySnapshot(
            observedAtMillis = nowMillis,
            batteryPercent = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            temperatureC = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            voltageMilliVolts = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            currentMicroAmps = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            averageCurrentMicroAmps = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            chargeCounterMicroAmpHours = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            energyCounterNanoWattHours = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            chargingStatus = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            plugged = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            health = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            capacityLevel = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            cycleCount = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            technology = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            present = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET)
        )
    }
}

/**
 * Platform thermal readings that do not come from the battery broadcast.
 *
 * [headroom] is dimensionless: 1.0 is the platform's SEVERE throttling
 * threshold and values above 1.0 are possible. It is **not** a temperature and
 * must never be rendered as one.
 */
data class ThermalSnapshot(
    val thermalStatus: Reading<ThermalStatusBand>,
    val headroom: Reading<Float>
) {
    companion object {
        fun notCollectedYet(): ThermalSnapshot = ThermalSnapshot(
            thermalStatus = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET),
            headroom = Reading.Absent(AbsenceReason.NOT_COLLECTED_YET)
        )
    }
}

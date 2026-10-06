package com.thermalsentinel.engine.domain

/**
 * Why a platform value is missing.
 *
 * The whole point of this type is to make "the device did not tell us" a
 * first-class, displayable outcome instead of a silent `0`, `null` or `-1`.
 * A real `0 mA` or `0 °C` reading and an unavailable sensor are different facts;
 * only documented sentinels are mapped to absence.
 *
 * Pure Kotlin by design: the domain layer never imports `android.*`, so every
 * mapping decision below is unit-testable on the JVM.
 */
enum class AbsenceReason(val storageValue: String) {
    /**
     * The platform API exists and was called, but this device did not report a
     * value (for example a battery-broadcast extra that the OEM omits).
     */
    NOT_REPORTED_BY_PLATFORM("not_reported"),

    /**
     * The underlying hardware/fuel-gauge does not expose this property.
     * `BatteryManager.get*Property` signals this with `Int.MIN_VALUE` /
     * `Long.MIN_VALUE`; `PowerManager.getThermalHeadroom` signals it with `NaN`.
     */
    UNSUPPORTED_ON_THIS_DEVICE("unsupported"),

    /**
     * A value was reported outside the physically plausible range for that
     * signal. Zero itself is retained wherever the platform can legitimately
     * report it.
     */
    REPORTED_VALUE_IMPLAUSIBLE("implausible"),

    /**
     * No collection has happened yet in this process. Distinct from
     * [NOT_REPORTED_BY_PLATFORM]: absence here is a state of the app, not of
     * the device.
     */
    NOT_COLLECTED_YET("not_collected"),

    /**
     * Collection was running in this process and has since been stopped. Distinct
     * from [NOT_COLLECTED_YET] (never started) and from [UNSUPPORTED_ON_THIS_DEVICE]
     * (the device cannot report it): the value a stale reader would find is a
     * *last* value from a dead session, and must not be presented as current.
     */
    SAMPLING_STOPPED("sampling_stopped"),

    /**
     * The value was absent when the sample was taken, and only the absence was
     * persisted. History rows cannot distinguish "unsupported" from "not
     * reported", and this engine does not guess backwards in time.
     */
    NOT_RECORDED("not_recorded")
}

/**
 * An immutable reading that is either present or absent-with-reason.
 *
 * Covariant in [T] so an `Absent` can be used wherever any reading is expected
 * without casts.
 */
sealed interface Reading<out T> {
    data class Present<T>(val value: T) : Reading<T>
    data class Absent(val reason: AbsenceReason) : Reading<Nothing>

    val valueOrNull: T?
        get() = (this as? Present)?.value

    val isPresent: Boolean
        get() = this is Present

    val absenceReason: AbsenceReason?
        get() = (this as? Absent)?.reason
}

fun <T> T.asReading(): Reading<T> = Reading.Present(this)

fun <T> Reading<T>.orElse(fallback: T): T = valueOrNull ?: fallback

/**
 * Human-readable fallback used by the UI. Kept here so every surface renders
 * absence with the same wording instead of inventing its own.
 */
val AbsenceReason.displayLabel: String
    get() = when (this) {
        AbsenceReason.NOT_REPORTED_BY_PLATFORM -> "Not reported"
        AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE -> "Unavailable on this device"
        AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE -> "Reported value not plausible"
        AbsenceReason.NOT_COLLECTED_YET -> "Not measured yet"
        AbsenceReason.SAMPLING_STOPPED -> "Monitoring is off"
        AbsenceReason.NOT_RECORDED -> "Not measured"
    }

package com.venom.stealthcam.domain.model

/**
 * Represents a hardware trigger action detected by [com.venom.stealthcam.core.trigger.VolumeKeyAccessibilityService].
 *
 * Using a flat [data class] instead of a sealed class makes it trivial to add new
 * actions (e.g. PAUSE, SNAPSHOT, TOGGLE) without structural changes to the event bus.
 *
 * @param action  What should happen as a result of this trigger.
 * @param camera  Which camera to use when [action] is [TriggerAction.START].
 *                Null for [TriggerAction.STOP] (the running camera doesn't change).
 */
data class TriggerEvent(
    val action: TriggerAction,
    val camera: CameraFacing? = null
)

/**
 * The set of actions a hardware trigger can request.
 * Keeping this as an enum (not sealed) allows exhaustive when-expressions and
 * easy future additions without breaking existing switch sites.
 */
enum class TriggerAction {
    /** Begin a new recording session with the camera specified in [TriggerEvent.camera]. */
    START,

    /** Stop the currently active recording session. */
    STOP
}

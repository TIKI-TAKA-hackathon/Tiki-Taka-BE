package xyz.stdiodh.gojjibom.notification

/**
 * Provider-abstracted outbound delivery of a caregiver notification (WP3b).
 *
 * A senior missing a dose produces a MISSED/ESCALATION [NotificationEntity]; that
 * notification is delivered to the care group's caregiver(s) — the 대표자(primary),
 * falling back to an active OWNER/FAMILY member — over Kakao 알림톡 or SMS.
 *
 * Only a STUB implementation exists today ([StubNotificationSender]). Real providers
 * are deferred: Kakao 알림톡 needs a business channel + a pre-approved template, and
 * SMS needs a provider API key. Selection is config-driven via
 * `app.notifications.provider` (stub|kakao|sms; default stub).
 *
 * Wording stays at the "약 미확인" (medication unconfirmed) level — non-diagnostic,
 * no fall/emergency overclaim (spec 005 §G).
 */
interface NotificationSender {
    /**
     * Delivers [notification] to a single caregiver recipient.
     *
     * @param targetPhone the recipient caregiver's phone number.
     * @param targetName the recipient caregiver's display name (for the template greeting).
     * @return a [DispatchResult] describing the channel used and success/failure.
     */
    fun dispatch(
        targetPhone: String,
        targetName: String,
        notification: NotificationEntity,
    ): DispatchResult
}

/** Delivery channels recorded on `notifications.dispatch_channel`. */
enum class DispatchChannel {
    STUB,
    KAKAO,
    SMS,
}

/**
 * Outcome of a single [NotificationSender.dispatch] call.
 *
 * [target] is the masked/opaque recipient reference persisted on
 * `notifications.dispatch_target` (kept short — VARCHAR(30) — and free of the raw
 * phone number so it is safe to store and expose).
 */
data class DispatchResult(
    val success: Boolean,
    val channel: DispatchChannel,
    val target: String,
)

/**
 * Renders the caregiver-facing message for a notification. Kept separate from the
 * transport so a real provider can map the same rendered text onto its template.
 * Wording stays at the non-diagnostic "약 미확인" level.
 */
object DispatchRenderer {
    fun renderTarget(targetPhone: String): String {
        val digits = targetPhone.filter { it.isDigit() }
        // Store only the last 4 digits so dispatch_target never holds a full phone number.
        val tail = if (digits.length >= TAIL_DIGITS) digits.takeLast(TAIL_DIGITS) else digits
        return "phone:***$tail"
    }

    private const val TAIL_DIGITS = 4
}

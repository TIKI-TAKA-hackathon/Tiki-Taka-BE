package xyz.stdiodh.gojjibom.notification

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Default [NotificationSender] used until a real provider is wired.
 *
 * It performs NO external I/O: it logs a single structured line and reports success
 * on channel STUB. This lets the escalation flow record a dispatch and stay
 * idempotent without provider credentials or template approval.
 *
 * Selected when `app.notifications.provider=stub` (the default, and the value when
 * the property is missing).
 *
 * TODO(WP3b/kakao): add a KakaoNotificationSender (@ConditionalOnProperty ...="kakao").
 *   Kakao 알림톡 requires a registered business channel (발신 프로필) AND a
 *   pre-approved template (템플릿 심사) — the message body must match the approved
 *   template exactly. Needs the Kakao business API key/secret.
 * TODO(WP3b/sms): add an SmsNotificationSender (@ConditionalOnProperty ...="sms")
 *   as the fallback channel. Needs a provider (e.g. NHN/Solapi/Twilio) API key and a
 *   verified sender number.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.notifications",
    name = ["provider"],
    havingValue = "stub",
    matchIfMissing = true,
)
class StubNotificationSender : NotificationSender {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun dispatch(
        targetPhone: String,
        targetName: String,
        notification: NotificationEntity,
    ): DispatchResult {
        val target = DispatchRenderer.renderTarget(targetPhone)
        // Structured, single-line, non-diagnostic ("약 미확인") — no raw phone in the log.
        log.info(
            "notification.dispatch STUB channel=STUB notificationId={} type={} level={} " +
                "careGroupId={} seniorId={} target={} targetName={} title=\"{}\"",
            notification.id,
            notification.type,
            notification.level,
            notification.careGroupId,
            notification.seniorId,
            target,
            targetName,
            notification.title,
        )
        return DispatchResult(success = true, channel = DispatchChannel.STUB, target = target)
    }
}

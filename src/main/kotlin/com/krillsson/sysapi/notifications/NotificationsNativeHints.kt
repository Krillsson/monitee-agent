package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.graphql.mutations.NotificationPolicyInput
import com.krillsson.sysapi.graphql.mutations.QuietHoursInput
import com.krillsson.sysapi.notifications.ntfy.NtfyApi
import com.krillsson.sysapi.notifications.webhook.WebhookPayload
import java.time.DayOfWeek
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    WebhookInfo::class,
    WebhookPayload::class,
    NtfyApi.Notification::class,
    NtfyApi.Notification.Action::class,
    MqttInfo::class,
    Notification.GenericEvent.ContainerImageUpdateAvailable::class,
    Notification.GenericEvent.ContainerImageUpdateDigest::class,
    NotificationPolicy::class,
    NotificationPolicyStore.StoredNotificationPolicy::class,
    NotificationPolicyStore.StoredNotificationPolicy.StoredQuietHours::class,
    QuietHours::class,
    DayOfWeek::class,
    SuppressedNotificationCounts::class,
    TestNotificationResult::class,
    NotificationPolicyInput::class,
    QuietHoursInput::class,
)
class NotificationsNativeHints

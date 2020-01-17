package arcs.sdk.android.dev.service.demo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

import javax.inject.Inject

import arcs.sdk.android.dev.api.Constants
import arcs.sdk.android.dev.api.PortableJson
import arcs.sdk.android.dev.api.PortableJsonParser
import arcs.sdk.android.dev.api.UiRenderer

class NotificationRenderer @Inject
internal constructor(private val context: Context, private val jsonParser: PortableJsonParser) : UiRenderer {

    init {
        val channel = NotificationChannel(CHANNEL_ID, "Arcs", NotificationManager.IMPORTANCE_DEFAULT)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    override fun render(packet: PortableJson): Boolean {
        val content = packet.getObject("content")
        val title = content.getString(TITLE_FIELD)
        val text = content.getString(TEXT_FIELD)

        Log.d(TAG, "Notification rendering: $title($text)")
        val builder = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.baseline_notification_important_black_18)
                .setContentTitle(title)
                .setContentText(text)

        val outputSlotId = packet.getString(OUTPUT_SLOT_ID_FIELD)
        // TODO(mmandlis): refactor to a generic method usable by other renderers as well.
        if (content.hasKey(TAP_HANDLER_FIELD)) {
            // Construct pendingIntent for notification tap.
            val handler = content.getString(TAP_HANDLER_FIELD)
            val pendingIntent = PendingIntent.getService(
                    context,
                    REQUEST_CODE_TAP,
                    getNotificationIntent(outputSlotId, handler),
                    PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentIntent(pendingIntent)
        }

        if (content.hasKey(DISMISS_HANDLER_FIELD)) {
            // Construct pendingIntent for notification dismiss.
            val handler = content.getString(DISMISS_HANDLER_FIELD)
            val pendingIntent = PendingIntent.getService(
                    context,
                    REQUEST_CODE_DISMISS,
                    getNotificationIntent(outputSlotId, handler),
                    PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setDeleteIntent(pendingIntent)
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        // TODO: Let particle control the notification id, in case it features multiple notifications.
        notificationManager.notify(outputSlotId.hashCode(), builder.build())

        return true
    }

    private fun getNotificationIntent(outputSlotId: String, handler: String): Intent {
        val intent = Intent(context, AndroidNotificationHandlerService::class.java)
        intent.action = outputSlotId
        intent.putExtra(Constants.INTENT_REFERENCE_ID_FIELD, outputSlotId)
        intent.putExtra(Constants.INTENT_EVENT_DATA_FIELD,
                jsonParser.stringify(
                        jsonParser.emptyObject().put(HANDLER_FIELD, handler)))

        return intent
    }

    companion object {

        private val REQUEST_CODE_TAP = 0
        private val REQUEST_CODE_DISMISS = 1
        private val TITLE_FIELD = "title"
        private val TEXT_FIELD = "text"
        private val TAP_HANDLER_FIELD = "tapHandler"
        private val DISMISS_HANDLER_FIELD = "dismissHandler"
        private val HANDLER_FIELD = "handler"
        private val OUTPUT_SLOT_ID_FIELD = "outputSlotId"
        private val CHANNEL_ID = "ArcsNotification"

        private val TAG = NotificationRenderer::class.java.simpleName
    }
}

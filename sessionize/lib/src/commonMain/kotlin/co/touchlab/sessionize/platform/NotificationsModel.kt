package co.touchlab.sessionize.platform

import co.touchlab.droidcon.db.MySessions
import co.touchlab.sessionize.Durations
import co.touchlab.sessionize.SettingsKeys.FEEDBACK_ENABLED
import co.touchlab.sessionize.SettingsKeys.LOCAL_NOTIFICATIONS_ENABLED
import co.touchlab.sessionize.SettingsKeys.REMINDERS_ENABLED
import co.touchlab.sessionize.api.NotificationsApi
import co.touchlab.sessionize.db.SessionizeDbHelper.sessionQueries
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import org.koin.core.KoinComponent
import org.koin.core.inject
import kotlin.native.concurrent.ThreadLocal

interface INotificationsModel {
    fun notificationsEnabled(): Boolean
    fun feedbackEnabled(): Boolean
    fun remindersEnabled(): Boolean
    fun setNotificationsEnabled(enabled: Boolean)
    fun setRemindersEnabled(enabled: Boolean)
    fun setFeedbackEnabled(enabled: Boolean)
    fun createNotifications()
    fun cancelNotifications()
    fun cancelFeedbackNotifications()
    fun cancelReminderNotifications(andDismissals: Boolean)
    fun recreateReminderNotifications()
    fun recreateFeedbackNotifications()
    fun getReminderTimeFromSession(session: MySessions): Long
    fun getReminderNotificationTitle(session: MySessions): String
    fun getReminderNotificationMessage(session: MySessions): String
    fun getFeedbackTimeFromSession(session: MySessions): Long
    fun getFeedbackNotificationTitle(): String
    fun getFeedbackNotificationMessage(): String
}

@ThreadLocal
object NotificationsModel : INotificationsModel, KoinComponent {

    private val appSettings: Settings by inject()
    private val notificationsApi: NotificationsApi by inject()

    // Settings

    override fun notificationsEnabled(): Boolean {
        return appSettings.getBoolean(LOCAL_NOTIFICATIONS_ENABLED, true)
    }

    override fun feedbackEnabled(): Boolean {
        return appSettings.getBoolean(FEEDBACK_ENABLED, true)
    }

    override fun remindersEnabled(): Boolean {
        return appSettings.getBoolean(LOCAL_NOTIFICATIONS_ENABLED, true) &&
                appSettings.getBoolean(REMINDERS_ENABLED, true)
    }

    override fun setNotificationsEnabled(enabled: Boolean) {
        appSettings[LOCAL_NOTIFICATIONS_ENABLED] = enabled
    }

    override fun setRemindersEnabled(enabled: Boolean) {
        appSettings[REMINDERS_ENABLED] = enabled
    }

    override fun setFeedbackEnabled(enabled: Boolean) {
        appSettings[FEEDBACK_ENABLED] = enabled
    }


    override fun createNotifications() {
        if(notificationsEnabled()) {
            recreateReminderNotifications()
            recreateFeedbackNotifications()
        }
    }

    override fun cancelNotifications() {
        cancelReminderNotifications(true)
        cancelFeedbackNotifications()
    }

    override fun cancelFeedbackNotifications() = notificationsApi.cancelFeedbackNotifications()
    override fun cancelReminderNotifications(andDismissals: Boolean) = notificationsApi.cancelReminderNotifications(andDismissals)

    override fun recreateReminderNotifications() {
        cancelReminderNotifications(false)
        if (remindersEnabled()){
            backgroundTask({ sessionQueries.mySessions().executeAsList() }) { mySessions ->
                if(mySessions.isNotEmpty()) {
                    notificationsApi.scheduleReminderNotificationsForSessions(mySessions)
                }
            }
        }
    }

    override fun recreateFeedbackNotifications() {
        cancelFeedbackNotifications()
        if (feedbackEnabled()){
            backgroundTask({ sessionQueries.mySessions().executeAsList() }) { mySessions ->
                if(mySessions.isNotEmpty()) {
                    notificationsApi.scheduleFeedbackNotificationsForSessions(mySessions)
                }
            }
        }
    }


    override fun getReminderTimeFromSession(session: MySessions): Long = session.startsAt.toLongMillis() - Durations.TEN_MINS_MILLIS
    override fun getReminderNotificationTitle(session: MySessions) = "Upcoming Event in ${session.roomName}"
    override fun getReminderNotificationMessage(session: MySessions) = "${session.title} is starting soon."

    override fun getFeedbackTimeFromSession(session: MySessions): Long = session.endsAt.toLongMillis() + Durations.TEN_MINS_MILLIS
    override fun getFeedbackNotificationTitle() = "Feedback Time!"
    override fun getFeedbackNotificationMessage() = "Your Feedback is Requested"
}
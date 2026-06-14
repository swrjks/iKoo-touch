package com.sudocode.ikoo.notification

import com.sudocode.ikoo.notifications.IKooNotificationListenerService

/**
 * Manifest-facing notification listener name for iKoo Event Detector.
 *
 * The implementation stays in IKooNotificationListenerService so notification
 * parsing, Gemma fallback, and calendar suggestion behavior are not duplicated.
 */
class EventNotificationListener : IKooNotificationListenerService()

package com.viplayer

import android.app.Application

/**
 * Application entry point.
 *
 * Media3 creates its own playback notification channel inside the session
 * service, so there is nothing to register eagerly here.
 */
class ViPlayerApp : Application()

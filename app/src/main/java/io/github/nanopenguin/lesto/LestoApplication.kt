package io.github.nanopenguin.lesto

import android.app.Application
import android.content.Context

class LestoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as LestoApplication).container

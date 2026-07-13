package com.sonicwave.remote.di

import android.content.Context
import com.sonicwave.remote.data.RemoteApi
import com.sonicwave.remote.data.SseClient
import com.sonicwave.remote.discovery.NsdDiscovery
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideRemoteApi(): RemoteApi = RemoteApi()

    @Provides
    @Singleton
    fun provideSseClient(remoteApi: RemoteApi): SseClient = SseClient(remoteApi)

    @Provides
    @Singleton
    fun provideNsdDiscovery(@ApplicationContext context: Context): NsdDiscovery =
        NsdDiscovery(context)
}

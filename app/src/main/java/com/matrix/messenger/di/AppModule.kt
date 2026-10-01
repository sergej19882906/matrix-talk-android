package com.matrix.messenger.di

import android.content.Context
import android.util.Log
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.data.repository.MatrixRepositoryImpl
import com.matrix.messenger.data.repository.SimpleRoomDisplayNameFallbackProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.MatrixConfiguration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val TAG = "AppModule"

    @Provides
    @Singleton
    fun provideMatrix(
        @ApplicationContext context: Context
    ): Matrix {
        val configuration = MatrixConfiguration(
            roomDisplayNameFallbackProvider = SimpleRoomDisplayNameFallbackProvider()
        )
        return try {
            Matrix(context, configuration)
        } catch (e: Exception) {
            Log.w(TAG, "Matrix init failed, clearing Realm files and retrying", e)
            clearRealmFiles(context)
            Matrix(context, configuration)
        }
    }

    @Provides
    @Singleton
    fun provideMatrixRepository(
        @ApplicationContext context: Context,
        matrix: Matrix
    ): MatrixRepository {
        return MatrixRepositoryImpl(context, matrix)
    }

    private fun clearRealmFiles(context: Context) {
        context.filesDir.listFiles()?.forEach { file ->
            if (file.name.contains(".realm")) {
                file.deleteRecursively()
            }
        }
    }
}

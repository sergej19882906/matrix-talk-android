package com.matrix.messenger.di

import android.content.Context
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

    @Provides
    @Singleton
    fun provideMatrix(
        @ApplicationContext context: Context
    ): Matrix {
        val configuration = MatrixConfiguration(
            roomDisplayNameFallbackProvider = SimpleRoomDisplayNameFallbackProvider()
        )
        return Matrix(context, configuration)
    }

    @Provides
    @Singleton
    fun provideMatrixRepository(
        @ApplicationContext context: Context,
        matrix: Matrix
    ): MatrixRepository {
        return MatrixRepositoryImpl(context, matrix)
    }
}

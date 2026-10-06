package com.sappho.audiobooks.sync

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import com.sappho.audiobooks.SapphoApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ProgressSyncWorker has an @AssistedInject constructor; WorkManager's default
 * factory can't build it, so every enqueue failed with "Could not create
 * Worker". These checks pin the Hilt wiring that fixes that.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerSetupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `application provides the WorkManager configuration`() {
        assertThat(Configuration.Provider::class.java.isAssignableFrom(SapphoApplication::class.java)).isTrue()
    }

    @Test
    fun `default WorkManager initializer is removed from the merged manifest`() {
        val provider = ComponentName(context, "androidx.startup.InitializationProvider")
        val metaData = try {
            context.packageManager.getProviderInfo(provider, PackageManager.GET_META_DATA).metaData
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val keys = metaData?.keySet().orEmpty()
        assertThat(keys).doesNotContain("androidx.work.WorkManagerInitializer")
    }

    @Test
    fun `worker built through a factory retries when the server is unreachable`() = runTest {
        val replayer = mockk<PendingProgressReplayer>()
        coEvery { replayer.replayAll() } returns PendingProgressReplayer.Result(synced = 0, dropped = 0, retryable = 2)
        val worker = TestListenableWorkerBuilder<ProgressSyncWorker>(context)
            .setWorkerFactory(factoryFor(replayer))
            .build()

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test
    fun `worker succeeds when everything synced`() = runTest {
        val replayer = mockk<PendingProgressReplayer>()
        coEvery { replayer.replayAll() } returns PendingProgressReplayer.Result(synced = 2, dropped = 0, retryable = 0)
        val worker = TestListenableWorkerBuilder<ProgressSyncWorker>(context)
            .setWorkerFactory(factoryFor(replayer))
            .build()

        assertThat(worker.doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    private fun factoryFor(replayer: PendingProgressReplayer) = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            ProgressSyncWorker(appContext, workerParameters, replayer)
    }
}

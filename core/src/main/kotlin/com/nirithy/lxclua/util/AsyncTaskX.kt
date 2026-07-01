package com.nirithy.lxclua.util

import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Process
import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.Volatile

/*
* Copyright (C) 2008 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/


/**
 *
 * AsyncTask enables proper and easy use of the UI thread. This class allows you
 * to perform background operations and publish results on the UI thread without
 * having to manipulate threads and/or handlers.
 *
 *
 * AsyncTask is designed to be a helper class around [Thread] and [Handler]
 * and does not constitute a generic threading framework. AsyncTasks should ideally be
 * used for short operations (a few seconds at the most.) If you need to keep threads
 * running for long periods of time, it is highly recommended you use the various APIs
 * provided by the `java.util.concurrent` package such as [Executor],
 * [ThreadPoolExecutor] and [FutureTask].
 *
 *
 * An asynchronous task is defined by a computation that runs on a background thread and
 * whose result is published on the UI thread. An asynchronous task is defined by 3 generic
 * types, called `Params`, `Progress` and `Result`,
 * and 4 steps, called `onPreExecute`, `doInBackground`,
 * `onProgressUpdate` and `onPostExecute`.
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 *
 * For more information about using tasks and threads, read the
 * [Processes and
 * Threads]({@docRoot}guide/components/processes-and-threads.html) developer guide.
</div> *
 *
 * <h2>Usage</h2>
 *
 * AsyncTask must be subclassed to be used. The subclass will override at least
 * one method ([.doInBackground]), and most often will override a
 * second one ([.onPostExecute].)
 *
 *
 * Here is an example of subclassing:
 * <pre class="prettyprint">
 * private class DownloadFilesTask extends AsyncTask&lt;URL, Integer, Long&gt; {
 * protected Long doInBackground(URL... urls) {
 * int count = urls.length;
 * long totalSize = 0;
 * for (int i = 0; i < count; i++) {
 * totalSize += Downloader.downloadFile(urls[i]);
 * publishProgress((int) ((i / (float) count) * 100));
 * // Escape early if cancel() is called
 * if (isCancelled()) break;
 * }
 * return totalSize;
 * }
 *
 * protected void onProgressUpdate(Integer... progress) {
 * setProgressPercent(progress[0]);
 * }
 *
 * protected void onPostExecute(Long result) {
 * showDialog("Downloaded " + result + " bytes");
 * }
 * }
</pre> *
 *
 *
 * Once created, a task is executed very simply:
 * <pre class="prettyprint">
 * new DownloadFilesTask().execute(url1, url2, url3);
</pre> *
 *
 * <h2>AsyncTask's generic types</h2>
 *
 * The three types used by an asynchronous task are the following:
 *
 *  1. `Params`, the type of the parameters sent to the task upon
 * execution.
 *  1. `Progress`, the type of the progress units published during
 * the background computation.
 *  1. `Result`, the type of the result of the background
 * computation.
 *
 *
 * Not all types are always used by an asynchronous task. To mark a type as unused,
 * simply use the type [Void]:
 * <pre>
 * private class MyTask extends AsyncTask&lt;Void, Void, Void&gt; { ... }
</pre> *
 *
 * <h2>The 4 steps</h2>
 *
 * When an asynchronous task is executed, the task goes through 4 steps:
 *
 *  1. [.onPreExecute], invoked on the UI thread before the task
 * is executed. This step is normally used to setup the task, for instance by
 * showing a progress bar in the user interface.
 *  1. [.doInBackground], invoked on the background thread
 * immediately after [.onPreExecute] finishes executing. This step is used
 * to perform background computation that can take a long time. The parameters
 * of the asynchronous task are passed to this step. The result of the computation must
 * be returned by this step and will be passed back to the last step. This step
 * can also use [.publishProgress] to publish one or more units
 * of progress. These values are published on the UI thread, in the
 * [.onProgressUpdate] step.
 *  1. [.onProgressUpdate], invoked on the UI thread after a
 * call to [.publishProgress]. The timing of the execution is
 * undefined. This method is used to display any form of progress in the user
 * interface while the background computation is still executing. For instance,
 * it can be used to animate a progress bar or show logs in a text field.
 *  1. [.onPostExecute], invoked on the UI thread after the background
 * computation finishes. The result of the background computation is passed to
 * this step as a parameter.
 *
 *
 * <h2>Cancelling a task</h2>
 *
 * A task can be cancelled at any time by invoking [.cancel]. Invoking
 * this method will cause subsequent calls to [.isCancelled] to return true.
 * After invoking this method, [.onCancelled], instead of
 * [.onPostExecute] will be invoked after [.doInBackground]
 * returns. To ensure that a task is cancelled as quickly as possible, you should always
 * check the return value of [.isCancelled] periodically from
 * [.doInBackground], if possible (inside a loop for instance.)
 *
 * <h2>Threading rules</h2>
 *
 * There are a few threading rules that must be followed for this class to
 * work properly:
 *
 *  * The AsyncTask class must be loaded on the UI thread. This is done
 * automatically as of [android.os.Build.VERSION_CODES.JELLY_BEAN].
 *  * The task instance must be created on the UI thread.
 *  * [.execute] must be invoked on the UI thread.
 *  * Do not call [.onPreExecute], [.onPostExecute],
 * [.doInBackground], [.onProgressUpdate] manually.
 *  * The task can be executed only once (an exception will be thrown if
 * a second execution is attempted.)
 *
 *
 * <h2>Memory observability</h2>
 *
 * AsyncTask guarantees that all callback calls are synchronized in such a way that the following
 * operations are safe without explicit synchronizations.
 *
 *  * Set member fields in the constructor or [.onPreExecute], and refer to them
 * in [.doInBackground].
 *  * Set member fields in [.doInBackground], and refer to them in
 * [.onProgressUpdate] and [.onPostExecute].
 *
 *
 * <h2>Order of execution</h2>
 *
 * When first introduced, AsyncTasks were executed serially on a single background
 * thread. Starting with [android.os.Build.VERSION_CODES.DONUT], this was changed
 * to a pool of threads allowing multiple tasks to operate in parallel. Starting with
 * [android.os.Build.VERSION_CODES.HONEYCOMB], tasks are executed on a single
 * thread to avoid common application errors caused by parallel execution.
 *
 * If you truly want parallel execution, you can invoke
 * [.executeOnExecutor] with
 * [.THREAD_POOL_EXECUTOR].
 */
abstract class AsyncTaskX<Params, Progress, Result> @JvmOverloads constructor(callbackLooper: Looper? = null as Looper?) {
    private val mWorker: WorkerRunnable<Params?, Result?>
    private val mFuture: FutureTask<Result?>

    /**
     * Returns the current status of this task.
     *
     * @return The current status.
     */
    @Volatile
    var status: Status = Status.PENDING
        private set

    private val mCancelled = AtomicBoolean()
    private val mTaskInvoked = AtomicBoolean()

    private val handler: Handler?

    private class SerialExecutor : Executor {
        val mTasks: ArrayDeque<Runnable?> = ArrayDeque<Runnable?>()
        var mActive: Runnable? = null

        @Synchronized
        override fun execute(r: Runnable) {
            mTasks.offer(object : Runnable {
                override fun run() {
                    try {
                        r.run()
                    } finally {
                        scheduleNext()
                    }
                }
            })
            if (mActive == null) {
                scheduleNext()
            }
        }

        @Synchronized
        protected fun scheduleNext() {
            if ((mTasks.poll().also { mActive = it }) != null) {
                THREAD_POOL_EXECUTOR.execute(mActive)
            }
        }
    }

    /**
     * Indicates the current status of the task. Each status will be set only once
     * during the lifetime of a task.
     */
    enum class Status {
        /**
         * Indicates that the task has not been executed yet.
         */
        PENDING,

        /**
         * Indicates that the task is running.
         */
        RUNNING,

        /**
         * Indicates that [AsyncTaskX.onPostExecute] has finished.
         */
        FINISHED,
    }

    /**
     * Creates a new asynchronous task. This constructor must be invoked on the UI thread.
     *
     * @hide
     */
    constructor(handler: Handler?) : this(if (handler != null) handler.getLooper() else null)

    /**
     * Creates a new asynchronous task. This constructor must be invoked on the UI thread.
     *
     * @hide
     */
    /**
     * Creates a new asynchronous task. This constructor must be invoked on the UI thread.
     */
    init {
        this.handler = if (callbackLooper == null || callbackLooper == Looper.getMainLooper())
            mainHandler
        else
            Handler(callbackLooper)

        mWorker = object : WorkerRunnable<Params?, Result?>() {
            @Throws(Exception::class)
            override fun call(): Result? {
                mTaskInvoked.set(true)
                var result: Result? = null
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    result = doInBackground(*mParams)
                    Binder.flushPendingCommands()
                } catch (tr: Throwable) {
                    mCancelled.set(true)
                    try {
                        throw tr
                    } catch (e: Throwable) {
                    }
                } finally {
                    postResult(result)
                }
                return result
            }
        }

        mFuture = object : FutureTask<Result?>(mWorker) {
            override fun done() {
                try {
                    postResultIfNotInvoked(get())
                } catch (e: InterruptedException) {
                    Log.w(LOG_TAG, e)
                } catch (e: ExecutionException) {
                    throw RuntimeException(
                        "An error occurred while executing doInBackground()",
                        e.cause
                    )
                } catch (e: CancellationException) {
                    postResultIfNotInvoked(null)
                }
            }
        }
    }

    private fun postResultIfNotInvoked(result: Result?) {
        val wasTaskInvoked = mTaskInvoked.get()
        if (!wasTaskInvoked) {
            postResult(result)
        }
    }

    private fun postResult(result: Result?): Result? {
        val message = this.handler!!.obtainMessage(
            MESSAGE_POST_RESULT,
            AsyncTaskX.AsyncTaskResult<Result?>(this, result)
        )
        message.sendToTarget()
        return result
    }

    /**
     * Override this method to perform a computation on a background thread. The
     * specified parameters are the parameters passed to [.execute]
     * by the caller of this task.
     *
     *
     * This method can call [.publishProgress] to publish updates
     * on the UI thread.
     *
     * @param params The parameters of the task.
     * @return A result, defined by the subclass of this task.
     * @see .onPreExecute
     * @see .onPostExecute
     *
     * @see .publishProgress
     */
    protected abstract fun doInBackground(vararg params: Params?): Result?

    /**
     * Runs on the UI thread before [.doInBackground].
     *
     * @see .onPostExecute
     *
     * @see .doInBackground
     */
    protected fun onPreExecute() {
    }

    /**
     *
     * Runs on the UI thread after [.doInBackground]. The
     * specified result is the value returned by [.doInBackground].
     *
     *
     * This method won't be invoked if the task was cancelled.
     *
     * @param result The result of the operation computed by [.doInBackground].
     * @see .onPreExecute
     *
     * @see .doInBackground
     *
     * @see .onCancelled
     */
    protected open fun onPostExecute(result: Result?) {
    }

    /**
     * Runs on the UI thread after [.publishProgress] is invoked.
     * The specified values are the values passed to [.publishProgress].
     *
     * @param values The values indicating progress.
     * @see .publishProgress
     *
     * @see .doInBackground
     */
    protected open fun onProgressUpdate(vararg values: Progress?) {
    }

    /**
     *
     * Runs on the UI thread after [.cancel] is invoked and
     * [.doInBackground] has finished.
     *
     *
     * The default implementation simply invokes [.onCancelled] and
     * ignores the result. If you write your own implementation, do not call
     * `super.onCancelled(result)`.
     *
     * @param result The result, if any, computed in
     * [.doInBackground], can be null
     * @see .cancel
     * @see .isCancelled
     */
    protected fun onCancelled(result: Result?) {
        onCancelled()
    }

    /**
     *
     * Applications should preferably override [.onCancelled].
     * This method is invoked by the default implementation of
     * [.onCancelled].
     *
     *
     * Runs on the UI thread after [.cancel] is invoked and
     * [.doInBackground] has finished.
     *
     * @see .onCancelled
     * @see .cancel
     * @see .isCancelled
     */
    protected fun onCancelled() {
    }

    val isCancelled: Boolean
        /**
         * Returns <tt>true</tt> if this task was cancelled before it completed
         * normally. If you are calling [.cancel] on the task,
         * the value returned by this method should be checked periodically from
         * [.doInBackground] to end the task as soon as possible.
         *
         * @return <tt>true</tt> if task was cancelled before it completed
         * @see .cancel
         */
        get() = mCancelled.get()

    /**
     *
     * Attempts to cancel execution of this task.  This attempt will
     * fail if the task has already completed, already been cancelled,
     * or could not be cancelled for some other reason. If successful,
     * and this task has not started when <tt>cancel</tt> is called,
     * this task should never run. If the task has already started,
     * then the <tt>mayInterruptIfRunning</tt> parameter determines
     * whether the thread executing this task should be interrupted in
     * an attempt to stop the task.
     *
     *
     * Calling this method will result in [.onCancelled] being
     * invoked on the UI thread after [.doInBackground]
     * returns. Calling this method guarantees that [.onPostExecute]
     * is never invoked. After invoking this method, you should check the
     * value returned by [.isCancelled] periodically from
     * [.doInBackground] to finish the task as early as
     * possible.
     *
     * @param mayInterruptIfRunning <tt>true</tt> if the thread executing this
     * task should be interrupted; otherwise, in-progress tasks are allowed
     * to complete.
     * @return <tt>false</tt> if the task could not be cancelled,
     * typically because it has already completed normally;
     * <tt>true</tt> otherwise
     * @see .isCancelled
     * @see .onCancelled
     */
    fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        mCancelled.set(true)
        return mFuture.cancel(mayInterruptIfRunning)
    }

    /**
     * Waits if necessary for the computation to complete, and then
     * retrieves its result.
     *
     * @return The computed result.
     * @throws CancellationException If the computation was cancelled.
     * @throws ExecutionException    If the computation threw an exception.
     * @throws InterruptedException  If the current thread was interrupted
     * while waiting.
     */
    @Throws(InterruptedException::class, ExecutionException::class)
    fun get(): Result? {
        return mFuture.get()
    }

    /**
     * Waits if necessary for at most the given time for the computation
     * to complete, and then retrieves its result.
     *
     * @param timeout Time to wait before cancelling the operation.
     * @param unit    The time unit for the timeout.
     * @return The computed result.
     * @throws CancellationException If the computation was cancelled.
     * @throws ExecutionException    If the computation threw an exception.
     * @throws InterruptedException  If the current thread was interrupted
     * while waiting.
     * @throws TimeoutException      If the wait timed out.
     */
    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    fun get(timeout: Long, unit: TimeUnit?): Result? {
        return mFuture.get(timeout, unit)
    }

    /**
     * Executes the task with the specified parameters. The task returns
     * itself (this) so that the caller can keep a reference to it.
     *
     *
     * Note: this function schedules the task on a queue for a single background
     * thread or pool of threads depending on the platform version.  When first
     * introduced, AsyncTasks were executed serially on a single background thread.
     * Starting with [android.os.Build.VERSION_CODES.DONUT], this was changed
     * to a pool of threads allowing multiple tasks to operate in parallel. Starting
     * [android.os.Build.VERSION_CODES.HONEYCOMB], tasks are back to being
     * executed on a single thread to avoid common application errors caused
     * by parallel execution.  If you truly want parallel execution, you can use
     * the [.executeOnExecutor] version of this method
     * with [.THREAD_POOL_EXECUTOR]; however, see commentary there for warnings
     * on its use.
     *
     *
     * This method must be invoked on the UI thread.
     *
     * @param params The parameters of the task.
     * @return This instance of AsyncTask.
     * @throws IllegalStateException If [.getStatus] returns either
     * [AsyncTaskX.Status.RUNNING] or [AsyncTaskX.Status.FINISHED].
     * @see .executeOnExecutor
     * @see .execute
     */
    fun execute(vararg params: Params?): AsyncTaskX<Params?, Progress?, Result?> {
        return executeOnExecutor(sDefaultExecutor, *params)
    }

    /**
     * Executes the task with the specified parameters. The task returns
     * itself (this) so that the caller can keep a reference to it.
     *
     *
     * This method is typically used with [.THREAD_POOL_EXECUTOR] to
     * allow multiple tasks to run in parallel on a pool of threads managed by
     * AsyncTask, however you can also use your own [Executor] for custom
     * behavior.
     *
     *
     * *Warning:* Allowing multiple tasks to run in parallel from
     * a thread pool is generally *not* what one wants, because the order
     * of their operation is not defined.  For example, if these tasks are used
     * to modify any state in common (such as writing a file due to a button click),
     * there are no guarantees on the order of the modifications.
     * Without careful work it is possible in rare cases for the newer version
     * of the data to be over-written by an older one, leading to obscure data
     * loss and stability issues.  Such changes are best
     * executed in serial; to guarantee such work is serialized regardless of
     * platform version you can use this function with [.SERIAL_EXECUTOR].
     *
     *
     * This method must be invoked on the UI thread.
     *
     * @param exec   The executor to use.  [.THREAD_POOL_EXECUTOR] is available as a
     * convenient process-wide thread pool for tasks that are loosely coupled.
     * @param params The parameters of the task.
     * @return This instance of AsyncTask.
     * @throws IllegalStateException If [.getStatus] returns either
     * [AsyncTaskX.Status.RUNNING] or [AsyncTaskX.Status.FINISHED].
     * @see .execute
     */
    fun executeOnExecutor(
        exec: Executor,
        vararg params: Params?
    ): AsyncTaskX<Params?, Progress?, Result?> {
        if (this.status != Status.PENDING) {
            when (this.status) {
                Status.RUNNING -> throw IllegalStateException(
                    "Cannot execute task:"
                            + " the task is already running."
                )

                Status.FINISHED -> throw IllegalStateException(
                    ("Cannot execute task:"
                            + " the task has already been executed "
                            + "(a task can be executed only once)")
                )
                Status.PENDING -> {} // 不会执行到这里，但Kotlin要求完备性
            }
        }

        this.status = Status.RUNNING

        onPreExecute()

        @Suppress("UNCHECKED_CAST")
        mWorker.mParams = params as Array<Params?>
        exec.execute(mFuture)

        @Suppress("UNCHECKED_CAST")
        return this as AsyncTaskX<Params?, Progress?, Result?>
    }

    /**
     * This method can be invoked from [.doInBackground] to
     * publish updates on the UI thread while the background computation is
     * still running. Each call to this method will trigger the execution of
     * [.onProgressUpdate] on the UI thread.
     *
     *
     * [.onProgressUpdate] will not be called if the task has been
     * canceled.
     *
     * @param values The progress values to update the UI with.
     * @see .onProgressUpdate
     *
     * @see .doInBackground
     */
    protected fun publishProgress(vararg values: Progress?) {
        if (!this.isCancelled) {
            this.handler!!.obtainMessage(
                MESSAGE_POST_PROGRESS,
                AsyncTaskX.AsyncTaskResult<Progress?>(this, *values)
            ).sendToTarget()
        }
    }

    private fun finish(result: Result?) {
        if (this.isCancelled) {
            onCancelled(result)
        } else {
            onPostExecute(result)
        }
        this.status = Status.FINISHED
    }

    @Suppress("UNCHECKED_CAST")
    private class InternalHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            val result = msg.obj as AsyncTaskResult<*>
            when (msg.what) {
                MESSAGE_POST_RESULT -> {                    // There is only one result
                    val task = result.mTask as AsyncTaskX<Any?, Any?, Any?>
                    task.finish(result.mData[0] as Any?)
                }
                MESSAGE_POST_PROGRESS -> {
                    val task = result.mTask as AsyncTaskX<Any?, Any?, Any?>
                    task.onProgressUpdate(*(result.mData as Array<out Any?>))
                }
            }
        }
    }

    private abstract class WorkerRunnable<Params, Result> : Callable<Result?> {
        lateinit var mParams: Array<Params?>
    }

    private class AsyncTaskResult<Data>(
        val mTask: AsyncTaskX<*, *, *>?,
        vararg val mData: Data?
    )

    companion object {
        private const val LOG_TAG = "AsyncTaskX"

        private val CPU_COUNT = Runtime.getRuntime().availableProcessors()

        // We want at least 2 threads and at most 4 threads in the core pool,
        // preferring to have 1 less than the CPU count to avoid saturating
        // the CPU with background work
        private const val CORE_POOL_SIZE = 1024
        private const val MAXIMUM_POOL_SIZE = 1024
        private const val KEEP_ALIVE_SECONDS = 30

        private val sThreadFactory: ThreadFactory = object : ThreadFactory {
            private val mCount = AtomicInteger(1)

            override fun newThread(r: Runnable?): Thread {
                return Thread(r, "AsyncTask #" + mCount.getAndIncrement())
            }
        }

        private val sPoolWorkQueue: BlockingQueue<Runnable?> =
            LinkedBlockingQueue<Runnable?>(1024 * 8)

        /**
         * An [Executor] that can be used to execute tasks in parallel.
         */
        val THREAD_POOL_EXECUTOR: Executor

        init {
            val threadPoolExecutor: ThreadPoolExecutor = ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS.toLong(), TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory
            )
            threadPoolExecutor.allowCoreThreadTimeOut(true)
            THREAD_POOL_EXECUTOR = threadPoolExecutor
        }

        /**
         * An [Executor] that executes tasks one at a time in serial
         * order.  This serialization is global to a particular process.
         */
        val SERIAL_EXECUTOR: Executor = SerialExecutor()

        private const val MESSAGE_POST_RESULT = 0x1
        private const val MESSAGE_POST_PROGRESS = 0x2

        @Volatile
        private var sDefaultExecutor: Executor = THREAD_POOL_EXECUTOR
        private var sHandler: InternalHandler? = null

        private val mainHandler: Handler
            get() {
                synchronized(AsyncTaskX::class.java) {
                    if (sHandler == null) {
                        sHandler =
                            InternalHandler(Looper.getMainLooper())
                    }
                    return sHandler!!
                }
            }

        /**
         * @hide
         */
        fun setDefaultExecutor(exec: Executor) {
            sDefaultExecutor = exec
        }

        /**
         * Convenience version of [.execute] for use with
         * a simple Runnable object. See [.execute] for more
         * information on the order of execution.
         *
         * @see .execute
         * @see .executeOnExecutor
         */
        fun execute(runnable: Runnable?) {
            sDefaultExecutor.execute(runnable)
        }
    }
}

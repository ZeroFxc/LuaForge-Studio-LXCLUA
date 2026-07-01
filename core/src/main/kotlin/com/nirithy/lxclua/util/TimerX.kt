/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.nirithy.lxclua.util

import java.util.Date

/**
 * Timers schedule one-shot or recurring [tasks][TimerTask] for execution.
 * Prefer [ ScheduledThreadPoolExecutor][java.util.concurrent.ScheduledThreadPoolExecutor] for new code.
 *
 *
 * Each timer has one thread on which tasks are executed sequentially. When
 * this thread is busy running a task, runnable tasks may be subject to delays.
 *
 *
 * One-shot are scheduled to run at an absolute time or after a relative
 * delay.
 *
 *
 * Recurring tasks are scheduled with either a fixed period or a fixed rate:
 *
 *  * With the default **fixed-period execution**, each
 * successive run of a task is scheduled relative to the start time of
 * the previous run, so two runs are never fired closer together in time
 * than the specified `period`.
 *  * With **fixed-rate execution**, the start time of each
 * successive run of a task is scheduled without regard for when the
 * previous run took place. This may result in a series of bunched-up runs
 * (one launched immediately after another) if delays prevent the timer
 * from starting tasks on time.
 *
 *
 *
 * When a timer is no longer needed, users should call [.cancel], which
 * releases the timer's thread and other resources. Timers not explicitly
 * cancelled may hold resources indefinitely.
 *
 *
 * This class does not offer guarantees about the real-time nature of task
 * scheduling. Multiple threads can share a single timer without
 * synchronization.
 */

open class TimerX @JvmOverloads constructor(name: String, isDaemon: Boolean = false) {
    private class TimerImpl(name: String, isDaemon: Boolean) : Thread() {
        private class TimerHeap {
            private val DEFAULT_HEAP_SIZE = 256

            internal var timers: Array<TimerTaskX?> = arrayOfNulls<TimerTaskX>(DEFAULT_HEAP_SIZE)

            private var size = 0

            internal var deletedCancelledNumber = 0

            fun minimum(): TimerTaskX {
                return timers[0]!!
            }

            val isEmpty: Boolean
                get() = size == 0

            fun insert(task: TimerTaskX?) {
                if (timers.size == size) {
                    val appendedTimers: Array<TimerTaskX?> = arrayOfNulls<TimerTaskX>(size * 2)
                    System.arraycopy(timers, 0, appendedTimers, 0, size)
                    timers = appendedTimers
                }
                timers[size++] = task!!
                upHeap()
            }

            fun delete(pos: Int) {
                // posible to delete any position of the heap
                if (pos >= 0 && pos < size) {
                    timers[pos] = timers[--size]
                    timers[size] = null
                    downHeap(pos)
                }
            }

            fun upHeap() {
                var current = size - 1
                var parent = (current - 1) / 2

                while (timers[current]!!.mWhen < timers[parent]!!.mWhen) {
                    // swap the two
                    val tmp = timers[current]
                    timers[current] = timers[parent]
                    timers[parent] = tmp

                    // update pos and current
                    current = parent
                    parent = (current - 1) / 2
                }
            }

            fun downHeap(pos: Int) {
                var current = pos
                var child = 2 * current + 1

                while (child < size && size > 0) {
                    // compare the children if they exist
                    if (child + 1 < size
                        && timers[child + 1]!!.mWhen < timers[child]!!.mWhen
                    ) {
                        child++
                    }

                    // compare selected child with parent
                    if (timers[current]!!.mWhen < timers[child]!!.mWhen) {
                        break
                    }

                    // swap the two
                    val tmp = timers[current]
                    timers[current] = timers[child]
                    timers[child] = tmp

                    // update pos and current
                    current = child
                    child = 2 * current + 1
                }
            }

            fun reset() {
                timers = arrayOfNulls<TimerTaskX>(DEFAULT_HEAP_SIZE)
                size = 0
            }

            fun adjustMinimum() {
                downHeap(0)
            }

            fun deleteIfCancelled() {
                var i = 0
                while (i < size) {
                    if (timers[i]!!.cancelled) {
                        deletedCancelledNumber++
                        delete(i)
                        // re-try this point
                        i--
                    }
                    i++
                }
            }

            fun getTask(task: TimerTaskX?): Int {
                for (i in timers.indices) {
                    if (timers[i] === task) {
                        return i
                    }
                }
                return -1
            }
        }

        /**
         * True if the method cancel() of the Timer was called or the !!!stop()
         * method was invoked
         */
        internal var cancelled = false

        /**
         * True if the Timer has become garbage
         */
        internal var finished = false

        /**
         * Contains scheduled events, sorted according to
         * `when` field of TaskScheduled object.
         */
        private val tasks = TimerHeap()

        /**
         * Starts a new timer.
         *
         * @param name     thread's name
         * @param isDaemon daemon thread or not
         */
        init {
            this.setName(name)
            this.setDaemon(isDaemon)
            this.start()
        }

        /**
         * This method will be launched on separate thread for each Timer
         * object.
         */
        override fun run() {
            while (true) {
                val task: TimerTaskX
                synchronized(this) {
                    // need to check cancelled inside the synchronized block
                    if (cancelled) {
                        return
                    }
                    if (tasks.isEmpty) {
                        if (finished) {
                            return
                        }
                        // no tasks scheduled -- sleep until any task appear
                        try {
                            (this as Object).wait()
                        } catch (ignored: InterruptedException) {
                        }
                        continue
                    }

                    val currentTime = System.currentTimeMillis()

                    task = tasks.minimum()
                    val timeToSleep: Long

                    synchronized(task.lock) {
                        if (task.cancelled) {
                            tasks.delete(0)
                            continue
                        }
                        // check the time to sleep for the first task scheduled
                        timeToSleep = task.mWhen - currentTime
                    }

                    if (timeToSleep > 0) {
                        // sleep!
                        try {
                            (this as Object).wait(timeToSleep)
                        } catch (ignored: InterruptedException) {
                        }
                        continue
                    }

                    // no sleep is necessary before launching the task
                    synchronized(task.lock) {
                        var pos = 0
                        if (tasks.minimum().mWhen != task.mWhen) {
                            pos = tasks.getTask(task)
                        }
                        if (task.cancelled) {
                            tasks.delete(tasks.getTask(task))
                            continue
                        }

                        // set time to schedule
                        task.setScheduledTime(task.mWhen)

                        // remove task from queue
                        tasks.delete(pos)

                        // set when the next task should be launched
                        if (task.period >= 0) {
                            // this is a repeating task,
                            if (task.fixedRate) {
                                // task is scheduled at fixed rate
                                task.mWhen = task.mWhen + task.period
                            } else {
                                // task is scheduled at fixed delay
                                task.mWhen = (System.currentTimeMillis()
                                        + task.period)
                            }

                            // insert this task into queue
                            insertTask(task)
                        } else {
                            task.mWhen = 0
                        }
                    }
                }

                var taskCompletedNormally = false
                try {
                    if (task.isEnabled) task.run()
                    taskCompletedNormally = true
                } finally {
                    if (!taskCompletedNormally) {
                        synchronized(this) {
                            cancelled = true
                        }
                    }
                }
            }
        }

        fun insertTask(newTask: TimerTaskX?) {
            // callers are synchronized
            tasks.insert(newTask)
            (this as Object).notify()
        }

        /**
         * Cancels timer.
         */
        @Synchronized
        fun cancel() {
            cancelled = true
            tasks.reset()
            (this as Object).notify()
        }

        fun purge(): Int {
            if (tasks.isEmpty) {
                return 0
            }
            // callers are synchronized
            tasks.deletedCancelledNumber = 0
            tasks.deleteIfCancelled()
            return tasks.deletedCancelledNumber
        }
    }

    @JvmRecord
    private data class FinalizerHelper(val impl: TimerImpl?) {
        @Throws(Throwable::class)
        protected fun finalize() {
            try {
                synchronized(impl!!) {
                    impl.finished = true
                    (impl as Object).notify()
                }
            } finally {
            }
        }
    }

    /* This object will be used in synchronization purposes */
    private val impl: TimerImpl

    // Used to finalize thread
    @Suppress("unused")
    private val finalizer: FinalizerHelper

    /**
     * Creates a new named `Timer` which may be specified to be run as a
     * daemon thread.
     *
     * @param name     the name of the `Timer`.
     * @param isDaemon true if `Timer`'s thread should be a daemon thread.
     * @throws NullPointerException is `name` is `null`
     */
    /**
     * Creates a new named `Timer` which does not run as a daemon thread.
     *
     * @param name the name of the Timer.
     * @throws NullPointerException is `name` is `null`
     */
    init {
        if (name == null) {
            throw NullPointerException("name is null")
        }
        this.impl = TimerImpl(name, isDaemon)
        this.finalizer = FinalizerHelper(impl)
    }

    /**
     * Creates a new `Timer` which may be specified to be run as a daemon thread.
     *
     * @param isDaemon `true` if the `Timer`'s thread should be a daemon thread.
     */
    /**
     * Creates a new non-daemon `Timer`.
     */
    @JvmOverloads
    constructor(isDaemon: Boolean = false) : this("Timer-" + nextId(), isDaemon)

    /**
     * Cancels the `Timer` and all scheduled tasks. If there is a
     * currently running task it is not affected. No more tasks may be scheduled
     * on this `Timer`. Subsequent calls do nothing.
     */
    fun cancel() {
        impl.cancel()
    }

    /**
     * Removes all canceled tasks from the task queue. If there are no
     * other references on the tasks, then after this call they are free
     * to be garbage collected.
     *
     * @return the number of canceled tasks that were removed from the task
     * queue.
     */
    fun purge(): Int {
        synchronized(impl) {
            return impl.purge()
        }
    }

    /**
     * Schedule a task for single execution. If `when` is less than the
     * current time, it will be scheduled to be executed as soon as possible.
     *
     * @param task the task to schedule.
     * @param when time of execution.
     * @throws IllegalArgumentException if `when.getTime() < 0`.
     * @throws IllegalStateException    if the `Timer` has been canceled, or if the task has been
     * scheduled or canceled.
     */
    fun schedule(task: TimerTaskX, `when`: Date) {
        require(`when`.getTime() >= 0)
        val delay = `when`.getTime() - System.currentTimeMillis()
        scheduleImpl(task, if (delay < 0) 0 else delay, -1, false)
    }

    /**
     * Schedule a task for single execution after a specified delay.
     *
     * @param task  the task to schedule.
     * @param delay amount of time in milliseconds before execution.
     * @throws IllegalArgumentException if `delay < 0`.
     * @throws IllegalStateException    if the `Timer` has been canceled, or if the task has been
     * scheduled or canceled.
     */
    fun schedule(task: TimerTaskX, delay: Long) {
        require(delay >= 0)
        scheduleImpl(task, delay, -1, false)
    }

    /**
     * Schedule a task for repeated fixed-delay execution after a specific delay.
     *
     * @param task   the task to schedule.
     * @param delay  amount of time in milliseconds before first execution.
     * @param period amount of time in milliseconds between subsequent executions.
     * @throws IllegalArgumentException if `delay < 0` or `period <= 0`.
     * @throws IllegalStateException    if the `Timer` has been canceled, or if the task has been
     * scheduled or canceled.
     */
    fun schedule(task: TimerTaskX, delay: Long, period: Long) {
        require(!(delay < 0 || period <= 0))
        scheduleImpl(task, delay, period, false)
    }

    /**
     * Schedule a task for repeated fixed-delay execution after a specific time
     * has been reached.
     *
     * @param task   the task to schedule.
     * @param when   time of first execution.
     * @param period amount of time in milliseconds between subsequent executions.
     * @throws IllegalArgumentException if `when.getTime() < 0` or `period <= 0`.
     * @throws IllegalStateException    if the `Timer` has been canceled, or if the task has been
     * scheduled or canceled.
     */
    fun schedule(task: TimerTaskX, `when`: Date, period: Long) {
        require(!(period <= 0 || `when`.getTime() < 0))
        val delay = `when`.getTime() - System.currentTimeMillis()
        scheduleImpl(task, if (delay < 0) 0 else delay, period, false)
    }

    /**
     * Schedule a task for repeated fixed-rate execution after a specific delay
     * has passed.
     *
     * @param task   the task to schedule.
     * @param delay  amount of time in milliseconds before first execution.
     * @param period amount of time in milliseconds between subsequent executions.
     * @throws IllegalArgumentException if `delay < 0` or `period <= 0`.
     * @throws IllegalStateException    if the `Timer` has been canceled, or if the task has been
     * scheduled or canceled.
     */
    fun scheduleAtFixedRate(task: TimerTaskX, delay: Long, period: Long) {
        require(!(delay < 0 || period <= 0))
        scheduleImpl(task, delay, period, true)
    }

    /**
     * Schedule a task for repeated fixed-rate execution after a specific time
     * has been reached.
     *
     * @param task   the task to schedule.
     * @param when   time of first execution.
     * @param period amount of time in milliseconds between subsequent executions.
     * @throws IllegalArgumentException if `when.getTime() < 0` or `period <= 0`.
     * @throws IllegalStateException    if the `Timer` has been canceled, or if the task has been
     * scheduled or canceled.
     */
    fun scheduleAtFixedRate(task: TimerTaskX, `when`: Date, period: Long) {
        require(!(period <= 0 || `when`.getTime() < 0))
        val delay = `when`.getTime() - System.currentTimeMillis()
        scheduleImpl(task, delay, period, true)
    }

    /*
     * Schedule a task.
     */
    private fun scheduleImpl(task: TimerTaskX, delay: Long, period: Long, fixed: Boolean) {
        synchronized(impl) {
            check(!impl.cancelled) { "Timer was canceled" }
            val `when` = delay + System.currentTimeMillis()

            require(`when` >= 0) { "Illegal delay to start the TimerTask: " + `when` }

            synchronized(task.lock) {
                check(!task.isScheduled) { "TimerTask is scheduled already" }
                check(!task.cancelled) { "TimerTask is canceled" }

                task.mWhen = `when`
                task.period = period
                task.fixedRate = fixed
            }

            // insert the newTask into queue
            impl.insertTask(task)
        }
    }

    companion object {
        private var timerId: Long = 0

        @Synchronized
        private fun nextId(): Long {
            return timerId++
        }
    }
}

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

/**
 * The `TimerTask` class represents a task to run at a specified time. The task
 * may be run once or repeatedly.
 *
 * @see Timer
 *
 * @see Object.wait
 */
abstract class TimerTaskX
/**
 * Creates a new `TimerTask`.
 */
protected constructor() : Runnable {
    /* Lock object for synchronization. It's also used by Timer class. */
    val lock: Any = Any()

    /* If timer was cancelled */
    var cancelled: Boolean = false

    /* Slots used by Timer */
    var mWhen: Long = 0

    var period: Long = 0

    var fixedRate: Boolean = false

    /*
     * The time when task will be executed, or the time when task was launched
     * if this is task in progress.
     */
    private var scheduledTime: Long = 0

    open var isEnabled: Boolean = false

    /*
     * Method called from the Timer object when scheduling an event @param time
     */
    fun setScheduledTime(time: Long) {
        synchronized(lock) {
            scheduledTime = time
        }
    }

    val isScheduled: Boolean
        /*
              * Is TimerTask scheduled into any timer?
              *
              * @return {@code true} if the timer task is scheduled, {@code false}
              * otherwise.
              */
        get() {
            synchronized(lock) {
                return mWhen > 0 || scheduledTime > 0
            }
        }

    /**
     * Cancels the `TimerTask` and removes it from the `Timer`'s queue. Generally, it
     * returns `false` if the call did not prevent a `TimerTask` from running at
     * least once. Subsequent calls have no effect.
     *
     * @return `true` if the call prevented a scheduled execution
     * from taking place, `false` otherwise.
     */
    open fun cancel(): Boolean {
        synchronized(lock) {
            val willRun = !cancelled && mWhen > 0
            cancelled = true
            return willRun
        }
    }

    /**
     * Returns the scheduled execution time. If the task execution is in
     * progress it returns the execution time of the ongoing task. Tasks which
     * have not yet run return an undefined value.
     *
     * @return the most recent execution time.
     */
    fun scheduledExecutionTime(): Long {
        synchronized(lock) {
            return scheduledTime
        }
    }

    /**
     * The task to run should be specified in the implementation of the `run()`
     * method.
     */
    abstract override fun run()
}

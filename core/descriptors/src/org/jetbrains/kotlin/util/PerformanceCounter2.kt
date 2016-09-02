/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.util

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import java.io.File
import java.lang.management.ManagementFactory
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * This counter is thread-safe for initialization and usage.
 * But it may calculate time and number of runs not precisely.
 */
abstract class PerformanceCounter2 protected constructor(val name: String) {
    companion object {
        private val allCounters = arrayListOf<PerformanceCounter2>()

        private var enabled = false

        fun currentTime(): Long = System.nanoTime()

        fun report(consumer: (String) -> Unit) {
            val countersCopy = synchronized(allCounters) {
                allCounters.toTypedArray()
            }
            countersCopy.forEach { it.report(consumer) }
        }

        fun setTimeCounterEnabled(enable: Boolean) {
            enabled = enable
        }

        fun resetAllCounters() {
            synchronized(allCounters) {
                allCounters.forEach {
                    it.reset()
                }
            }
        }

        @JvmOverloads fun create(name: String, reenterable: Boolean = false): PerformanceCounter2 {
            return if (reenterable)
                ReenterableCounter2(name)
            else
                SimpleCounter2(name)
        }


        fun log(owner: DeclarationDescriptor, kind: String, count: Int) {
            File("/home/sufix/names.txt").appendText("$owner $kind $count\n")
        }

        fun create(name: String, vararg excluded: PerformanceCounter2): PerformanceCounter2 = CounterWithExclude2(name, *excluded)

        internal inline fun <T> getOrPut(threadLocal: ThreadLocal<T>, default: () -> T) : T {
            var value = threadLocal.get()
            if (value == null) {
                value = default()
                threadLocal.set(value)
            }
            return value
        }
    }

    internal val excludedFrom: MutableList<CounterWithExclude2> = ArrayList()

    private var count: Int = 0
    private var totalTimeNanos: Long = 0

    init {
        synchronized(allCounters) {
            allCounters.add(this)
        }
    }

    fun increment() {
        count++
    }

    fun <T> time(block: () -> T): T {
        count++
        if (!enabled) return block()

        excludedFrom.forEach { it.enterExcludedMethod() }
        try {
            return countTime(block)
        }
        finally {
            excludedFrom.forEach { it.exitExcludedMethod() }
        }
    }

    fun reset() {
        count = 0
        totalTimeNanos = 0
    }

    protected fun incrementTime(delta: Long) {
        totalTimeNanos += delta
    }

    protected abstract fun <T> countTime(block: () -> T): T

    fun report(consumer: (String) -> Unit) {
        if (totalTimeNanos == 0L) {
            consumer("$name performed $count times")
        }
        else {
            val millis = TimeUnit.NANOSECONDS.toMillis(totalTimeNanos)
            consumer("$name performed $count times, total time $millis ms")
        }
    }
}

private class SimpleCounter2(name: String): PerformanceCounter2(name) {
    override fun <T> countTime(block: () -> T): T {
        val startTime = PerformanceCounter2.currentTime()
        try {
            return block()
        }
        finally {
            incrementTime(PerformanceCounter2.currentTime() - startTime)
        }
    }
}

private class ReenterableCounter2(name: String): PerformanceCounter2(name) {
    companion object {
        private val enteredCounters = ThreadLocal<MutableSet<ReenterableCounter2>>()

        private fun enterCounter(counter: ReenterableCounter2) = PerformanceCounter2.getOrPut(enteredCounters) { HashSet() }.add(counter)

        private fun leaveCounter(counter: ReenterableCounter2) {
            enteredCounters.get()?.remove(counter)
        }
    }

    override fun <T> countTime(block: () -> T): T {
        val startTime = PerformanceCounter2.currentTime()
        val needTime = enterCounter(this)
        try {
            return block()
        }
        finally {
            if (needTime) {
                incrementTime(PerformanceCounter2.currentTime() - startTime)
                leaveCounter(this)
            }
        }
    }
}

/**
 *  This class allows to calculate pure time for some method excluding some other methods.
 *  For example, we can calculate total time for CallResolver excluding time for getTypeInfo().
 *
 *  Main and excluded methods may be reenterable.
 */
internal class CounterWithExclude2(name: String, vararg excludedCounters: PerformanceCounter2): PerformanceCounter2(name) {
    companion object {
        private val counterToCallStackMapThreadLocal = ThreadLocal<MutableMap<CounterWithExclude2, CallStackWithTime>>()

        private fun getCallStack(counter: CounterWithExclude2)
                = PerformanceCounter2.getOrPut(counterToCallStackMapThreadLocal) { HashMap() }.getOrPut(counter) { CallStackWithTime() }
    }

    init {
        excludedCounters.forEach { it.excludedFrom.add(this) }
    }

    private val callStack: CallStackWithTime
        get() = getCallStack(this)

    override fun <T> countTime(block: () -> T): T {
        incrementTime(callStack.push(true))
        try {
            return block()
        }
        finally {
            incrementTime(callStack.pop(true))
        }
    }

    fun enterExcludedMethod() {
        incrementTime(callStack.push(false))
    }

    fun exitExcludedMethod() {
        incrementTime(callStack.pop(false))
    }

    private class CallStackWithTime {
        private val callStack = Stack<Boolean>()
        private var intervalStartTime: Long = 0

        fun Stack<Boolean>.peekOrFalse() = if (isEmpty()) false else peek()

        private fun intervalUsefulTime(callStackUpdate: Stack<Boolean>.() -> Unit): Long {
            val delta = if (callStack.peekOrFalse()) PerformanceCounter2.currentTime() - intervalStartTime else 0
            callStack.callStackUpdate()

            intervalStartTime = PerformanceCounter2.currentTime()
            return delta
        }

        fun push(usefulCall: Boolean): Long {
            if (!isEnteredCounter() && !usefulCall) return 0

            return intervalUsefulTime { push(usefulCall) }
        }

        fun pop(usefulCall: Boolean): Long {
            if (!isEnteredCounter()) return 0

            assert(callStack.peek() == usefulCall)
            return intervalUsefulTime { pop() }
        }

        fun isEnteredCounter(): Boolean = !callStack.isEmpty()
    }
}

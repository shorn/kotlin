///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package org.jetbrains.kotlin.storage

/**
 * Base class for hashtables that use open addressing to resolve
 * collisions.

 * Created: Wed Nov 28 21:11:16 2001

 * @author Eric D. Friedman
 * *
 * @version $Id: MyTHash.java,v 1.5 2004/09/24 09:11:15 cdr Exp $
 */

abstract class MyTHash
@JvmOverloads constructor(
        initialCapacity: Int = MyTHash.DEFAULT_INITIAL_CAPACITY,
        protected val _loadFactor: Float = MyTHash.DEFAULT_LOAD_FACTOR
) {
    /** the current number of occupied slots in the hash.  */
    @Transient protected var _size: Int = 0

    /** the current number of free slots in the hash.  */
    @Transient protected var _free: Int = 0

    /**
     * Number of entries marked REMOVED (by either TObjectHash or TPrimitiveHash)
     */
    @Transient protected var _deadkeys: Int = 0

    /**
     * The maximum number of elements allowed without allocating more
     * space.
     */
    protected var _maxSize: Int = 0

    init {
        setUp((initialCapacity / _loadFactor).toInt() + 1)
    }

    /**
     * Tells whether this set is currently holding any elements.

     * @return a `boolean` value
     */
    val isEmpty: Boolean
        get() = 0 == _size

    /**
     * Returns the number of distinct elements in this collection.

     * @return an `int` value
     */
    open fun size(): Int {
        return _size
    }

    /**
     * @return the current physical capacity of the hash table.
     */
    protected abstract fun capacity(): Int

    /**
     * Ensure that this hashtable has sufficient capacity to hold
     * desiredCapacity **additional** elements without
     * requiring a rehash.  This is a tuning method you can call
     * before doing a large insert.

     * @param desiredCapacity an `int` value
     */
    fun ensureCapacity(desiredCapacity: Int) {
        if (desiredCapacity > _maxSize - size()) {
            rehash(MyPrimeFinder.nextPrime((desiredCapacity + size() / _loadFactor).toInt() + 2))
            computeMaxSize(capacity())
        }
    }

    /**
     * initializes the hashtable to a prime capacity which is at least
     * initialCapacity + 1.

     * @param initialCapacity an `int` value
     * *
     * @return the actual capacity chosen
     */
    protected open fun setUp(initialCapacity: Int): Int {
        val capacity = MyPrimeFinder.nextPrime(initialCapacity)
        computeMaxSize(capacity)
        return capacity
    }

    /**
     * Rehashes the set.

     * @param newCapacity an `int` value
     */
    protected abstract fun rehash(newCapacity: Int)

    /**
     * Computes the values of maxSize. There will always be at least
     * one free slot required.

     * @param capacity an `int` value
     */
    private fun computeMaxSize(capacity: Int) {
        // need at least one free slot for open addressing
        _maxSize = Math.min(capacity - 1, (capacity * _loadFactor).toInt())
        _free = capacity - _size // reset the free element count
        _deadkeys = 0
    }

    /**
     * After an insert, this hook is called to adjust the size/free
     * values of the set and to perform rehashing if necessary.
     */
    protected fun postInsertHook(usedFreeSlot: Boolean) {
        if (usedFreeSlot) {
            _free--
        }
        else {
            _deadkeys--
        }

        // rehash whenever we exhaust the available space in the table
        if (++_size > _maxSize || _free == 0) {
            rehash(MyPrimeFinder.nextPrime(calculateGrownCapacity()))
            computeMaxSize(capacity())
        }
    }

    protected fun calculateGrownCapacity(): Int {
        return capacity() shl 1
    }

    companion object {

        /**
         * the load above which rehashing occurs.
         */
        protected val DEFAULT_LOAD_FACTOR = 0.8f

        /** the default initial capacity for the hash table.  This is one
         * less than a prime value because one is added to it when
         * searching for a prime capacity to account for the free slot
         * required by open addressing. Thus, the real default capacity is
         * 11.
         */
        protected val DEFAULT_INITIAL_CAPACITY = 4
    }
}

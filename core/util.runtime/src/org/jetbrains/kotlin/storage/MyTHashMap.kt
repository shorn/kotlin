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
 * An implementation of the Map interface which uses an open addressed
 * hash table to store its contents.

 * Created: Sun Nov  4 08:52:45 2001

 * @author Eric D. Friedman
 */
open class MyTHashMap<in K : Any, V> : MyTHash() {

    protected lateinit var _entries: Array<Any?>

    protected fun computeHashCode(o: Any) = o.hashCode()
    protected fun computeEquals(o1: Any, o2: Any) = o1 == o2

    override fun capacity() = _entries.keysCapacity

    /**
     * Searches the set for obj

     * @param obj an `Object` value
     * *
     * @return a `boolean` value
     */
    open operator fun contains(obj: Any): Boolean {
        return index(obj as K) >= 0
    }

    /**
     * Locates the index of obj.

     * @param obj an `Object` value
     * *
     * @return the index of obj or -1 if it isn't in the set.
     */
    protected fun index(obj: K): Int {
        val set = _entries
        val length = set.keysCapacity
        val hash = computeHashCode(obj) and 0x7fffffff
        var index = hash % length
        var cur: Any? = set.getKey(index)

        if (cur != null && !computeEquals(cur, obj)) {
            // see Knuth, p. 529
            val probe = 1 + hash % (length - 2)

            do {
                index -= probe
                if (index < 0) {
                    index += length
                }
                cur = set.getKey(index)
            }
            while (cur != null && !computeEquals(cur, obj))
        }

        return if (cur == null) -1 else index
    }

    /**
     * Locates the index at which obj can be inserted.  if
     * there is already a value equal()ing obj in the set,
     * returns that value's index as -index - 1.

     * @param obj an `Object` value
     * *
     * @return the index of a FREE slot at which obj can be inserted
     * * or, if obj is already stored in the hash, the negative value of
     * * that index, minus 1: -index -1.
     */
    protected fun insertionIndex(obj: K): Int {
        val set = _entries
        val length = set.keysCapacity
        val hash = computeHashCode(obj) and 0x7fffffff
        var index = hash % length
        var cur: Any? = set.getKey(index) ?: return index       // empty, all done

        if (computeEquals(cur!!, obj)) {
            return -index - 1   // already stored
        }

        // already FULL or REMOVED, must probe
        // compute the double hash
        val probe = 1 + hash % (length - 2)

        // starting at the natural offset, probe until we find an
        // offset that isn't full.
        do {
            index -= probe
            if (index < 0) {
                index += length
            }
            cur = set.getKey(index)
        }
        while (cur != null && !computeEquals(cur, obj))

        // if it's full, the key is already stored
        if (cur != null) {
            return -index - 1
        }

        return index
    }

    protected fun Array<Any?>.setKey(index: Int, k: Any) {
        object {

        }
        this[index * 2] = k
    }
    protected fun Array<Any?>.setValue(index: Int, v: Any?) {
        this[index * 2 + 1] = v
    }

    protected fun Array<Any?>.getKey(index: Int): Any? {
        return this[index * 2]
    }

    protected fun Array<Any?>.getValue(index: Int): Any? {
        return this[index * 2 + 1]
    }

    protected val Array<*>.keysCapacity: Int
        get() = size / 2

    /**
     * initialize the value array of the map.

     * @param initialCapacity an `int` value
     * *
     * @return an `int` value
     */
    override fun setUp(initialCapacity: Int): Int {
        val capacity = super.setUp(initialCapacity)
        _entries = arrayOfNulls<Any>(2 * capacity)
        return capacity
    }

    /**
     * Inserts a key/value pair into the map.

     * @param key an `Object` value
     * *
     * @param value an `Object` value
     * *
     * @return the previous value associated with key,
     * * or null if none was found.
     */
    fun put(key: K?, value: V): V? {
        if (null == key) {
            throw NullPointerException("null keys not supported")
        }
        var previous: V? = null
        var index = insertionIndex(key)
        val alreadyStored = index < 0
        if (alreadyStored) {
            index = -index - 1
            previous = _entries.getValue(index) as V?
        }
        val oldKey = _entries.getKey(index)
        _entries.setKey(index, key)
        _entries.setValue(index, value)
        if (!alreadyStored) {
            postInsertHook(oldKey == null)
        }

        return previous
    }

   /**
     * rehashes the map to the new capacity.

     * @param newCapacity an `int` value
     */
   override fun rehash(newCapacity: Int) {
       val oldCapacity = capacity()
       val oldEntries = _entries
       _entries = arrayOfNulls<Any>(newCapacity * 2)
       val set = _entries

       var i = oldCapacity
       while (i-- > 0) {
           if (oldEntries.getKey(i) != null) {
               val o = oldEntries.getKey(i)
               val index = insertionIndex(o as K)
               assert(index >= 0)
               set.setKey(index, o)
               set.setValue(index, oldEntries.getValue(i))
           }
       }
   }

    /**
     * retrieves the value for key

     * @param key an `Object` value
     * *
     * @return the value of key or null if no such mapping exists.
     */
    operator fun get(key: Any): V? {
        val index = index(key as K)
        return if (index < 0) null else _entries.getValue(index) as V?
    }


    /**
     * checks for the present of key in the keys of the map.

     * @param key an `Object` value
     * *
     * @return a `boolean` value
     */
    fun containsKey(key: Any): Boolean {
        return contains(key)
    }

}

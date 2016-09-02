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
open class MyTHashMap<in K : Any, V> : MyTObjectHash<K>() {
    /** the values of the  map  */
    protected lateinit var _values: Array<V>

    /**
     * initialize the value array of the map.

     * @param initialCapacity an `int` value
     * *
     * @return an `int` value
     */
    override fun setUp(initialCapacity: Int): Int {
        val capacity = super.setUp(initialCapacity)
        _values = arrayOfNulls<Any>(capacity) as Array<V>
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
            previous = _values[index]
        }
        val oldKey = _set[index]
        _set[index] = key
        _values[index] = value
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
        val oldCapacity = _set.size
        val oldKeys = _set
        val oldVals = _values

        _set = arrayOfNulls<Any>(newCapacity)
        _values = arrayOfNulls<Any>(newCapacity) as Array<V>

        var i = oldCapacity
        while (i-- > 0) {
            if (oldKeys[i] != null) {
                val o = oldKeys[i]
                val index = insertionIndex(o as K)
                assert(index >= 0)
                _set[index] = o
                _values[index] = oldVals[i]
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
        return if (index < 0) null else _values[index]
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

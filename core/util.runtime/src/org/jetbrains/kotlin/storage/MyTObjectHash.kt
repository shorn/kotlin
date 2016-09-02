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
 * An open addressed hashing implementation for Object types.

 * Created: Sun Nov  4 08:56:06 2001

 * @author Eric D. Friedman
 * *
 * @version $Id: TObjectHash.java,v 1.8 2004/09/24 09:11:15 cdr Exp $
 */
abstract class MyTObjectHash<in T : Any> : MyTHash() {
    /** the set of Objects  */
    protected lateinit var _set: Array<Any?>

    private object NULL {}

    protected fun computeHashCode(o: Any) = o.hashCode()
    protected fun computeEquals(o1: Any, o2: Any) = o1 == o2

    override fun capacity(): Int {
        return _set.size
    }

    /**
     * initializes the Object set of this hash table.

     * @param initialCapacity an `int` value
     * *
     * @return an `int` value
     */
    override fun setUp(initialCapacity: Int): Int {
        val capacity = super.setUp(initialCapacity)
        _set = arrayOfNulls<Any>(capacity)
        return capacity
    }

    /**
     * Searches the set for obj

     * @param obj an `Object` value
     * *
     * @return a `boolean` value
     */
    open operator fun contains(obj: Any): Boolean {
        return index(obj as T) >= 0
    }

    /**
     * Locates the index of obj.

     * @param obj an `Object` value
     * *
     * @return the index of obj or -1 if it isn't in the set.
     */
    protected fun index(obj: T): Int {
        val set = _set
        val length = set.size
        val hash = computeHashCode(obj) and 0x7fffffff
        var index = hash % length
        var cur: Any? = set[index]

        if (cur != null && !computeEquals(cur, obj)) {
            // see Knuth, p. 529
            val probe = 1 + hash % (length - 2)

            do {
                index -= probe
                if (index < 0) {
                    index += length
                }
                cur = set[index]
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
    protected fun insertionIndex(obj: T): Int {
        val set = _set
        val length = set.size
        val hash = computeHashCode(obj) and 0x7fffffff
        var index = hash % length
        var cur: Any? = set[index] ?: return index       // empty, all done

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
            cur = set[index]
        }
        while (cur != null && !computeEquals(cur, obj))

        // if it's full, the key is already stored
        if (cur != null) {
            return -index - 1
        }

        return index
    }
}

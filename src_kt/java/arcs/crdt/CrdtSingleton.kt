/*
 * Copyright 2019 Google LLC.
 *
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 *
 * Code distributed by Google as part of this project is also subject to an additional IP rights
 * grant found at
 * http://polymer.github.io/PATENTS.txt
 */

package arcs.crdt

import arcs.crdt.CrdtSet.Operation.Add
import arcs.crdt.CrdtSet.Operation.Remove
import arcs.crdt.internal.Actor
import arcs.crdt.internal.Referencable
import arcs.crdt.internal.VersionMap

/** A [CrdtModel] capable of managing a mutable reference. */
class CrdtSingleton<T : Referencable> : CrdtModel<CrdtSet.Data<T>, CrdtSingleton.Operation<T>, T?> {
  private val set: CrdtSet<T> = CrdtSet()

  override val data: CrdtSet.Data<T>
    get() = set.data
  override val consumerView: T?
    // Get any value, or null if no value is present.
    get() = set.consumerView.minBy { it.id }

  override fun merge(other: CrdtSet.Data<T>): MergeChanges<CrdtSet.Data<T>, Operation<T>> {
    set.merge(other)
    // Always return CrdtChange.Data change records, since we cannot perform an op-based change.
    return MergeChanges(CrdtChange.Data(data), CrdtChange.Data(data))
  }

  override fun applyOperation(op: Operation<T>): Boolean = op.applyTo(set)

  override fun updateData(newData: CrdtSet.Data<T>) = set.updateData(newData)

  sealed class Operation<T : Referencable>(
    open val actor: Actor,
    open val clock: VersionMap
  ) : CrdtOperation {
    /** Mutates [data] based on the implementation of the [Operation]. */
    internal abstract fun applyTo(set: CrdtSet<T>): Boolean

    /** An [Operation] to update the value stored by the [CrdtSingleton]. */
    data class Update<T : Referencable> internal constructor(
      override val actor: Actor,
      override val clock: VersionMap,
      val value: T
    ) : Operation<T>(actor, clock) {
      override fun applyTo(set: CrdtSet<T>): Boolean {
        // Remove does not require an increment, but the caller of this method will have
        // incremented its version, so we hack a version with t-1 for this actor.
        val removeClock = VersionMap(clock)
        removeClock[actor]--

        // If we can't remove all existing values, we can't update the value.
        if (!Clear<T>(actor, removeClock).applyTo(set)) return false

        // After removal of all existing values, we simply need to add the new value.
        return set.applyOperation(Add(clock, actor, value))
      }
    }

    /** An [Operation] to clear the value stored by the [CrdtSingleton]. */
    data class Clear<T : Referencable> internal constructor(
      override val actor: Actor,
      override val clock: VersionMap
    ) : Operation<T>(actor, clock) {
      override fun applyTo(set: CrdtSet<T>): Boolean {
        // Clear all existing values if our clock allows it.

        val removeOps = set.originalData.values
          .map { (_, value) -> Remove(clock, actor, value.value) }

        removeOps.forEach { set.applyOperation(it) }
        return true
      }
    }
  }
}

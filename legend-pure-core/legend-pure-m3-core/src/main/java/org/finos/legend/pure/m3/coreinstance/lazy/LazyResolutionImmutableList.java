// Copyright 2025 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.pure.m3.coreinstance.lazy;

import org.eclipse.collections.api.block.HashingStrategy;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.function.Function2;
import org.eclipse.collections.api.block.function.primitive.BooleanFunction;
import org.eclipse.collections.api.block.function.primitive.ByteFunction;
import org.eclipse.collections.api.block.function.primitive.CharFunction;
import org.eclipse.collections.api.block.function.primitive.DoubleFunction;
import org.eclipse.collections.api.block.function.primitive.FloatFunction;
import org.eclipse.collections.api.block.function.primitive.IntFunction;
import org.eclipse.collections.api.block.function.primitive.LongFunction;
import org.eclipse.collections.api.block.function.primitive.ShortFunction;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.block.predicate.Predicate2;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.block.procedure.primitive.ObjectIntProcedure;
import org.eclipse.collections.api.collection.MutableCollection;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.factory.Stacks;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.ParallelListIterable;
import org.eclipse.collections.api.list.primitive.ImmutableBooleanList;
import org.eclipse.collections.api.list.primitive.ImmutableByteList;
import org.eclipse.collections.api.list.primitive.ImmutableCharList;
import org.eclipse.collections.api.list.primitive.ImmutableDoubleList;
import org.eclipse.collections.api.list.primitive.ImmutableFloatList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.ImmutableShortList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.multimap.list.ImmutableListMultimap;
import org.eclipse.collections.api.ordered.OrderedIterable;
import org.eclipse.collections.api.partition.list.PartitionImmutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.stack.MutableStack;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.block.factory.Predicates;
import org.eclipse.collections.impl.collection.immutable.AbstractImmutableCollection;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.lazy.ReverseIterable;
import org.eclipse.collections.impl.lazy.parallel.list.ListIterableParallelIterable;
import org.eclipse.collections.impl.list.immutable.ImmutableListIterator;
import org.eclipse.collections.impl.partition.list.PartitionImmutableListImpl;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.eclipse.collections.impl.utility.OrderedIterate;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

abstract class LazyResolutionImmutableList<T> extends AbstractImmutableCollection<T> implements ImmutableList<T>, List<T>, RandomAccess
{
    private final Object[] items;

    private LazyResolutionImmutableList(Object[] items)
    {
        this.items = items;
    }

    @Override
    public boolean equals(Object other)
    {
        return (other == this) ||
                ((other instanceof List) && ListIterate.equals(this, (List<?>) other));
    }

    @Override
    public int hashCode()
    {
        int hash = 1;
        for (int i = toArrayIndex(0), end = toArrayIndex(size()); i < end; i++)
        {
            hash = (31 * hash) + Objects.hashCode(getResolvedByArrayIndex(i));
        }
        return hash;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c)
    {
        throw new UnsupportedOperationException("Cannot call addAll() on " + getClass().getSimpleName());
    }

    @Override
    public T set(int index, T element)
    {
        throw new UnsupportedOperationException("Cannot call set() on " + getClass().getSimpleName());
    }

    @Override
    public void add(int index, T element)
    {
        throw new UnsupportedOperationException("Cannot call add() on " + getClass().getSimpleName());
    }

    @Override
    public T remove(int index)
    {
        throw new UnsupportedOperationException("Cannot call remove() on " + getClass().getSimpleName());
    }

    @Override
    public void replaceAll(UnaryOperator<T> operator)
    {
        throw new UnsupportedOperationException("Cannot call replaceAll() on " + getClass().getSimpleName());
    }

    @Override
    public boolean contains(Object o)
    {
        return anySatisfy((o == null) ? Objects::isNull : o::equals);
    }

    @Override
    public boolean containsAll(Collection<?> source)
    {
        switch (source.size())
        {
            case 0:
            {
                return true;
            }
            case 1:
            {
                return contains(Iterate.getFirst(source));
            }
            default:
            {
                return containsAllInternalSet(Sets.mutable.withAll(source));
            }
        }
    }

    @Override
    public boolean containsAllIterable(Iterable<?> source)
    {
        MutableSet<Object> set = Sets.mutable.withAll(source);
        switch (set.size())
        {
            case 0:
            {
                return true;
            }
            case 1:
            {
                return contains(set.getAny());
            }
            default:
            {
                return containsAllInternalSet(set);
            }
        }
    }

    @Override
    public boolean containsAllArguments(Object... elements)
    {
        switch (elements.length)
        {
            case 0:
            {
                return true;
            }
            case 1:
            {
                return contains(elements[0]);
            }
            default:
            {
                return containsAllInternalSet(Sets.mutable.with(elements));
            }
        }
    }

    private boolean containsAllInternalSet(MutableSet<Object> set)
    {
        return anySatisfy(item -> set.remove(item) && set.isEmpty());
    }

    @Override
    public int size()
    {
        return this.items.length;
    }

    @Override
    public T get(int index)
    {
        return getResolved(checkBounds(index));
    }

    @Override
    public int indexOf(Object object)
    {
        for (int i = 0, size = size(); i < size; i++)
        {
            if (Objects.equals(getResolved(i), object))
            {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o)
    {
        for (int i = size() - 1; i >= 0; i--)
        {
            if (Objects.equals(getResolved(i), o))
            {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Iterator<T> iterator()
    {
        return listIterator(0);
    }

    @Override
    public ListIterator<T> listIterator()
    {
        return listIterator(0);
    }

    @Override
    public ListIterator<T> listIterator(int index)
    {
        return new ImmutableListIterator<>(this, index);
    }

    @Override
    public MutableStack<T> toStack()
    {
        return Stacks.mutable.withAll(this);
    }

    @Override
    public ImmutableList<T> toImmutable()
    {
        return this;
    }

    @Override
    public T getFirst()
    {
        return isEmpty() ? null : get(0);
    }

    @Override
    public T getLast()
    {
        int size = size();
        return (size == 0) ? null : get(size - 1);
    }

    @Override
    public void forEach(Consumer<? super T> consumer)
    {
        for (int i = toArrayIndex(0), end = toArrayIndex(size()); i < end; i++)
        {
            consumer.accept(getResolvedByArrayIndex(i));
        }
    }

    @Override
    public void each(Procedure<? super T> procedure)
    {
        forEach((Consumer<? super T>) procedure);
    }

    @Override
    public void forEach(int startIndex, int endIndex, Procedure<? super T> procedure)
    {
        ListIterate.rangeCheck(startIndex, endIndex, size());
        int arrayStart = toArrayIndex(startIndex);
        int arrayEnd = toArrayIndex(endIndex);
        if (arrayStart <= arrayEnd)
        {
            for (int i = arrayStart; i <= arrayEnd; i++)
            {
                procedure.value(getResolvedByArrayIndex(i));
            }
        }
        else
        {
            for (int i = arrayStart; i >= arrayEnd; i--)
            {
                procedure.value(getResolvedByArrayIndex(i));
            }
        }
    }

    @Override
    public void forEachWithIndex(int fromIndex, int toIndex, ObjectIntProcedure<? super T> procedure)
    {
        ListIterate.rangeCheck(fromIndex, toIndex, size());
        if (fromIndex <= toIndex)
        {
            for (int i = fromIndex; i <= toIndex; i++)
            {
                procedure.value(getResolved(i), i);
            }
        }
        else
        {
            for (int i = fromIndex; i >= toIndex; i--)
            {
                procedure.value(getResolved(i), i);
            }
        }
    }

    @Override
    public ImmutableList<T> tap(Procedure<? super T> procedure)
    {
        each(procedure);
        return this;
    }

    @Override
    public T detect(Predicate<? super T> predicate)
    {
        for (int i = toArrayIndex(0), end = toArrayIndex(size()); i < end; i++)
        {
            T item = getResolvedByArrayIndex(i);
            if (predicate.accept(item))
            {
                return item;
            }
        }
        return null;
    }

    @Override
    public Optional<T> detectOptional(Predicate<? super T> predicate)
    {
        for (int i = toArrayIndex(0), end = toArrayIndex(size()); i < end; i++)
        {
            T item = getResolvedByArrayIndex(i);
            if (predicate.accept(item))
            {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    @Override
    public int detectIndex(Predicate<? super T> predicate)
    {
        for (int i = 0, end = size(); i < end; i++)
        {
            if (predicate.accept(getResolved(i)))
            {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int detectLastIndex(Predicate<? super T> predicate)
    {
        for (int i = size() - 1; i >= 0; i--)
        {
            if (predicate.accept(getResolved(i)))
            {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean anySatisfy(Predicate<? super T> predicate)
    {
        return shortCircuit(predicate, true, true, false);
    }

    @Override
    public boolean allSatisfy(Predicate<? super T> predicate)
    {
        return shortCircuit(predicate, false, false, true);
    }

    @Override
    public boolean noneSatisfy(Predicate<? super T> predicate)
    {
        return shortCircuit(predicate, true, false, true);
    }

    @Override
    public <P> boolean anySatisfyWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        return anySatisfy(Predicates.bind(predicate, parameter));
    }

    @Override
    public <P> boolean allSatisfyWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        return allSatisfy(Predicates.bind(predicate, parameter));
    }

    @Override
    public <P> boolean noneSatisfyWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        return noneSatisfy(Predicates.bind(predicate, parameter));
    }

    @Override
    public ImmutableList<T> select(Predicate<? super T> predicate)
    {
        return select(predicate, Lists.mutable.empty()).toImmutable();
    }

    @Override
    public <P> ImmutableList<T> selectWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        return select(Predicates.bind(predicate, parameter));
    }

    @Override
    public ImmutableList<T> reject(Predicate<? super T> predicate)
    {
        return reject(predicate, Lists.mutable.empty()).toImmutable();
    }

    @Override
    public <P> ImmutableList<T> rejectWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        return reject(Predicates.bind(predicate, parameter));
    }

    @Override
    public PartitionImmutableList<T> partition(Predicate<? super T> predicate)
    {
        return ListIterate.partition(this, predicate).toImmutable();
    }

    @Override
    public <P> PartitionImmutableList<T> partitionWith(Predicate2<? super T, ? super P> predicate, P parameter)
    {
        return ListIterate.partitionWith(this, predicate, parameter).toImmutable();
    }

    @Override
    public <S> ImmutableList<S> selectInstancesOf(Class<S> clazz)
    {
        return ListIterate.selectInstancesOf(this, clazz).toImmutable();
    }

    @Override
    public <V> ImmutableList<V> collect(Function<? super T, ? extends V> function)
    {
        return collect(function, Lists.mutable.<V>ofInitialCapacity(size())).toImmutable();
    }

    @Override
    public ImmutableBooleanList collectBoolean(BooleanFunction<? super T> booleanFunction)
    {
        return ListIterate.collectBoolean(this, booleanFunction).toImmutable();
    }

    @Override
    public ImmutableByteList collectByte(ByteFunction<? super T> byteFunction)
    {
        return ListIterate.collectByte(this, byteFunction).toImmutable();
    }

    @Override
    public ImmutableCharList collectChar(CharFunction<? super T> charFunction)
    {
        return ListIterate.collectChar(this, charFunction).toImmutable();
    }

    @Override
    public ImmutableDoubleList collectDouble(DoubleFunction<? super T> doubleFunction)
    {
        return ListIterate.collectDouble(this, doubleFunction).toImmutable();
    }

    @Override
    public ImmutableFloatList collectFloat(FloatFunction<? super T> floatFunction)
    {
        return ListIterate.collectFloat(this, floatFunction).toImmutable();
    }

    @Override
    public ImmutableIntList collectInt(IntFunction<? super T> intFunction)
    {
        return ListIterate.collectInt(this, intFunction).toImmutable();
    }

    @Override
    public ImmutableLongList collectLong(LongFunction<? super T> longFunction)
    {
        return ListIterate.collectLong(this, longFunction).toImmutable();
    }

    @Override
    public ImmutableShortList collectShort(ShortFunction<? super T> shortFunction)
    {
        return ListIterate.collectShort(this, shortFunction).toImmutable();
    }

    @Override
    public <P, V> ImmutableList<V> collectWith(Function2<? super T, ? super P, ? extends V> function, P parameter)
    {
        return ListIterate.collectWith(this, function, parameter, Lists.mutable.<V>ofInitialCapacity(size())).toImmutable();
    }

    @Override
    public <V> ImmutableList<V> collectIf(Predicate<? super T> predicate, Function<? super T, ? extends V> function)
    {
        return ListIterate.collectIf(this, predicate, function, Lists.mutable.<V>empty()).toImmutable();
    }

    @Override
    public <V> ImmutableList<V> flatCollect(Function<? super T, ? extends Iterable<V>> function)
    {
        return ListIterate.flatCollect(this, function).toImmutable();
    }

    @Override
    public <V> ImmutableListMultimap<V, T> groupBy(Function<? super T, ? extends V> function)
    {
        return ListIterate.groupBy(this, function, Multimaps.mutable.list.<V, T>empty()).toImmutable();
    }

    @Override
    public <V> ImmutableListMultimap<V, T> groupByEach(Function<? super T, ? extends Iterable<V>> function)
    {
        return ListIterate.groupByEach(this, function, Multimaps.mutable.list.empty()).toImmutable();
    }

    @Override
    public ImmutableList<T> distinct()
    {
        return ListIterate.distinct(this).toImmutable();
    }

    @Override
    public ImmutableList<T> distinct(HashingStrategy<? super T> hashingStrategy)
    {
        return ListIterate.distinct(this, hashingStrategy).toImmutable();
    }

    @Override
    public <V> ImmutableList<T> distinctBy(Function<? super T, ? extends V> function)
    {
        return ListIterate.distinctBy(this, function).toImmutable();
    }

    @Override
    public <S> boolean corresponds(OrderedIterable<S> other, Predicate2<? super T, ? super S> predicate)
    {
        return OrderedIterate.corresponds(this, other, predicate);
    }

    @Override
    public <S> ImmutableList<Pair<T, S>> zip(Iterable<S> that)
    {
        return ListIterate.zip(this, that).toImmutable();
    }

    @Override
    public ImmutableList<Pair<T, Integer>> zipWithIndex()
    {
        return ListIterate.zipWithIndex(this).toImmutable();
    }

    @Override
    public ImmutableList<T> take(int count)
    {
        if (count == 0)
        {
            return Lists.immutable.empty();
        }
        if (count >= size())
        {
            return this;
        }
        return subList(0, count);
    }

    @Override
    public ImmutableList<T> takeWhile(Predicate<? super T> predicate)
    {
        int index = detectIndex(Predicates.not(predicate));
        return (index == -1) ? this : subList(0, index);
    }

    @Override
    public ImmutableList<T> drop(int count)
    {
        if (count == 0)
        {
            return this;
        }
        if (count >= size())
        {
            return Lists.immutable.empty();
        }
        return subList(count, size());
    }

    @Override
    public ImmutableList<T> dropWhile(Predicate<? super T> predicate)
    {
        int index = detectIndex(Predicates.not(predicate));
        return (index == -1) ? this : subList(index, size());
    }

    @Override
    public PartitionImmutableList<T> partitionWhile(Predicate<? super T> predicate)
    {
        int index = detectIndex(Predicates.not(predicate));
        switch (index)
        {
            case -1:
            {
                return new PartitionImmutableListImpl<>(this, Lists.immutable.empty());
            }
            case 0:
            {
                return new PartitionImmutableListImpl<>(Lists.immutable.empty(), this);
            }
            default:
            {
                return new PartitionImmutableListImpl<>(subList(0, index), subList(index, size()));
            }
        }
    }

    @Override
    public List<T> castToList()
    {
        return this;
    }

    @Override
    public LazyResolutionImmutableList<T> subList(int fromIndex, int toIndex)
    {
        if (fromIndex < 0)
        {
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        }
        if (toIndex > size())
        {
            throw new IndexOutOfBoundsException("toIndex = " + toIndex);
        }
        if (fromIndex > toIndex)
        {
            throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ')');
        }
        return new LazyResolutionImmutableSubList<>(this.items, toArrayIndex(fromIndex), toArrayIndex(toIndex));
    }

    @Override
    public ReverseIterable<T> asReversed()
    {
        return ReverseIterable.adapt(this);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ImmutableList<T> toReversed()
    {
        int size = size();
        Object[] reversed = new Object[size];
        boolean anyUnresolved = false;
        for (int i = 0; i < size; i++)
        {
            int targetIndex = size - i - 1;
            Object item = this.items[toArrayIndex(i)];
            if (item instanceof LazyResolver)
            {
                LazyResolver<?> supplier = (LazyResolver<?>) item;
                if (supplier.isResolved())
                {
                    reversed[targetIndex] = supplier.getResolvedValue();
                }
                else
                {
                    anyUnresolved = true;
                    reversed[targetIndex] = item;
                }
            }
            else
            {
                reversed[targetIndex] = item;
            }
        }
        return anyUnresolved ?
               new SimpleLazyResolutionImmutableList<>(reversed) :
               Lists.immutable.with((T[]) reversed);
    }

    @Override
    public ParallelListIterable<T> asParallel(ExecutorService executorService, int batchSize)
    {
        return new ListIterableParallelIterable<>(this, executorService, batchSize);
    }

    @Override
    public int binarySearch(T key, Comparator<? super T> comparator)
    {
        return Collections.binarySearch(this, key, comparator);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ImmutableList<T> newWith(T element)
    {
        int size = size();
        Object[] newItems = new Object[size + 1];
        boolean anyUnresolved = false;
        for (int i = 0; i < size; i++)
        {
            Object item = this.items[toArrayIndex(i)];
            if (item instanceof LazyResolver)
            {
                LazyResolver<?> supplier = (LazyResolver<?>) item;
                if (supplier.isResolved())
                {
                    newItems[i] = supplier.getResolvedValue();
                }
                else
                {
                    anyUnresolved = true;
                    newItems[i] = item;
                }
            }
            else
            {
                newItems[i] = item;
            }
        }
        newItems[size] = element;
        return anyUnresolved ?
               new SimpleLazyResolutionImmutableList<>(newItems) :
               Lists.immutable.with((T[]) newItems);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ImmutableList<T> newWithout(T element)
    {
        int index = indexOf(element);
        if (index == -1)
        {
            return this;
        }

        int size = size();
        Object[] newItems = new Object[size - 1];
        for (int i = 0; i < index; i++)
        {
            newItems[i] = getResolved(i);
        }
        boolean anyUnresolved = false;
        for (int i = index + 1; i < size; i++)
        {
            int targetIndex = i - 1;
            Object item = this.items[toArrayIndex(i)];
            if (item instanceof LazyResolver)
            {
                LazyResolver<?> supplier = (LazyResolver<?>) item;
                if (supplier.isResolved())
                {
                    newItems[targetIndex] = supplier.getResolvedValue();
                }
                else
                {
                    anyUnresolved = true;
                    newItems[targetIndex] = item;
                }
            }
            else
            {
                newItems[targetIndex] = item;
            }
        }
        return anyUnresolved ?
               new SimpleLazyResolutionImmutableList<>(newItems) :
               Lists.immutable.with((T[]) newItems);
    }

    @SuppressWarnings("unchecked")
    @Override
    public ImmutableList<T> newWithAll(Iterable<? extends T> elements)
    {
        int oldSize = size();
        int newSize = oldSize + Iterate.sizeOf(elements);
        if (newSize == oldSize)
        {
            return this;
        }

        Object[] newItems = new Object[newSize];
        boolean anyUnresolved = false;
        int i = 0;
        while (i < oldSize)
        {
            Object item = this.items[toArrayIndex(i)];
            if (item instanceof LazyResolver)
            {
                LazyResolver<?> supplier = (LazyResolver<?>) item;
                if (supplier.isResolved())
                {
                    newItems[i] = supplier.getResolvedValue();
                }
                else
                {
                    anyUnresolved = true;
                    newItems[i] = item;
                }
            }
            else
            {
                newItems[i] = item;
            }
            i++;
        }
        for (T newElement : elements)
        {
            newItems[i++] = newElement;
        }
        return anyUnresolved ?
               new SimpleLazyResolutionImmutableList<>(newItems) :
               Lists.immutable.with((T[]) newItems);
    }

    @Override
    public ImmutableList<T> newWithoutAll(Iterable<? extends T> elements)
    {
        Set<? extends T> toRemove = (elements instanceof Set) ? (Set<? extends T>) elements : Sets.mutable.withAll(elements);
        MutableList<T> copy = null;
        for (int i = 0, size = size(); i < size; i++)
        {
            T item = getResolved(i);
            if (toRemove.contains(item))
            {
                if (copy == null)
                {
                    if (i > 0)
                    {
                        copy = Lists.mutable.ofInitialCapacity(i);
                        for (int j = 0; j < i; j++)
                        {
                            copy.add(getResolved(j));
                        }
                    }
                    else
                    {
                        copy = Lists.mutable.empty();
                    }
                }
            }
            else if (copy != null)
            {
                copy.add(item);
            }
        }
        return (copy == null) ? this : copy.toImmutable();
    }

    @Override
    protected MutableCollection<T> newMutable(int size)
    {
        return Lists.mutable.ofInitialCapacity(size);
    }

    protected abstract int toArrayIndex(int index);

    private T getResolved(int index)
    {
        return getResolvedByArrayIndex(toArrayIndex(index));
    }

    @SuppressWarnings("unchecked")
    private T getResolvedByArrayIndex(int arrayIndex)
    {
        Object item = this.items[arrayIndex];
        return (item instanceof LazyResolver) ?
               ((LazyResolver<? extends T>) item).get() :
               (T) item;
    }

    @SuppressWarnings("unchecked")
    private boolean shortCircuit(Predicate<? super T> predicate, boolean expected, boolean onShortCircuit, boolean atEnd)
    {
        int end = toArrayIndex(size());
        MutableIntList skippedIntervals = IntLists.mutable.empty();
        boolean skipped = false;
        for (int i = toArrayIndex(0); i < end; i++)
        {
            Object item = this.items[i];
            if (item instanceof LazyResolver)
            {
                LazyResolver<? extends T> supplier = (LazyResolver<? extends T>) item;
                if (supplier.isResolved())
                {
                    T value = supplier.getResolvedValue();
                    if (predicate.accept(value) == expected)
                    {
                        return onShortCircuit;
                    }
                    if (skipped)
                    {
                        skippedIntervals.add(i);
                        skipped = false;
                    }
                }
                else if (!skipped)
                {
                    skippedIntervals.add(i);
                    skipped = true;
                }
            }
            else if (predicate.accept((T) item) == expected)
            {
                return onShortCircuit;
            }
            else if (skipped)
            {
                skippedIntervals.add(i);
                skipped = false;
            }
        }
        if (skippedIntervals.notEmpty())
        {
            int skippedIntsIndex = 0;
            int lastSkippedIntsIndex = skippedIntervals.size() - 1;
            while (skippedIntsIndex <= lastSkippedIntsIndex)
            {
                int from = skippedIntervals.get(skippedIntsIndex++);
                int to = (skippedIntsIndex <= lastSkippedIntsIndex) ? skippedIntervals.get(skippedIntsIndex++) : end;
                for (int i = from; i < to; i++)
                {
                    if (predicate.test(getResolvedByArrayIndex(i)) == expected)
                    {
                        return onShortCircuit;
                    }
                }
            }
        }
        return atEnd;
    }

    private int checkBounds(int index)
    {
        if ((index < 0) || (index >= size()))
        {
            throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
        }
        return index;
    }

    private static class SimpleLazyResolutionImmutableList<T> extends LazyResolutionImmutableList<T>
    {
        private SimpleLazyResolutionImmutableList(Object[] items)
        {
            super(items);
        }

        @Override
        protected int toArrayIndex(int index)
        {
            return index;
        }
    }

    private static class LazyResolutionImmutableSubList<T> extends LazyResolutionImmutableList<T>
    {
        private final int offset;
        private final int size;

        private LazyResolutionImmutableSubList(Object[] items, int fromIndex, int toIndex)
        {
            super(items);
            this.offset = fromIndex;
            this.size = toIndex - fromIndex;
        }

        @Override
        public int size()
        {
            return this.size;
        }

        @Override
        protected int toArrayIndex(int index)
        {
            return index + this.offset;
        }
    }

    static <T> LazyResolutionImmutableList<T> newList(ListIterable<? extends Supplier<? extends T>> suppliers)
    {
        Object[] items = new Object[suppliers.size()];
        suppliers.forEachWithIndex((supplier, i) -> items[i] = LazyResolver.fromSupplier(supplier));
        return new SimpleLazyResolutionImmutableList<>(items);
    }

    @SafeVarargs
    static <T> LazyResolutionImmutableList<T> newList(Supplier<? extends T>... suppliers)
    {
        int size = suppliers.length;
        Object[] items = new Object[size];
        for (int i = 0; i < size; i++)
        {
            items[i] = LazyResolver.fromSupplier(suppliers[i]);
        }
        return new SimpleLazyResolutionImmutableList<>(items);
    }

    static <T> LazyResolutionImmutableList<T> newList(Supplier<? extends T> supplier)
    {
        return new SimpleLazyResolutionImmutableList<>(new Object[]{LazyResolver.fromSupplier(supplier)});
    }
}

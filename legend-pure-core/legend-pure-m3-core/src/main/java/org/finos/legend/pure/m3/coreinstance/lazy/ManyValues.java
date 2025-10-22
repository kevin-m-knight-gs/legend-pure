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

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.block.HashingStrategy;
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
import org.eclipse.collections.api.multimap.list.ImmutableListMultimap;
import org.eclipse.collections.api.ordered.OrderedIterable;
import org.eclipse.collections.api.partition.list.PartitionImmutableList;
import org.eclipse.collections.api.stack.MutableStack;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.collection.immutable.AbstractImmutableCollection;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.lazy.ReverseIterable;
import org.eclipse.collections.impl.lazy.parallel.list.ListIterableParallelIterable;
import org.eclipse.collections.impl.list.immutable.ImmutableListIterator;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.ListIterate;
import org.eclipse.collections.impl.utility.OrderedIterate;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.indexing.IDConflictException;
import org.finos.legend.pure.m4.coreinstance.indexing.IndexSpecification;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class ManyValues<T> implements PropertyValue<T>
{
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<ManyValues, ImmutableList> UPDATER = AtomicReferenceFieldUpdater.newUpdater(ManyValues.class, ImmutableList.class, "values");

    private volatile ImmutableList<T> values;

    private ManyValues(ImmutableList<T> values)
    {
        this.values = values;
    }

    @Override
    public boolean hasValue()
    {
        return this.values.notEmpty();
    }

    @Override
    public T getValue()
    {
        switch (this.values.size())
        {
            case 0:
            {
                return null;
            }
            case 1:
            {
                return this.values.get(0);
            }
            default:
            {
                throw new IllegalStateException("Expected at most 1 value, found " + this.values.size());
            }
        }
    }

    @Override
    public ListIterable<T> getValues()
    {
        return this.values;
    }

    @Override
    public <K> CoreInstance getValueByIDIndex(IndexSpecification<K> indexSpec, K key) throws IDConflictException
    {
        return getValueByIDIndex(indexSpec, key, null);
    }

    @Override
    public <K> CoreInstance getValueByIDIndex(IndexSpecification<K> indexSpec, K key, Function<? super T, ? extends CoreInstance> toCoreInstance) throws IDConflictException
    {
        CoreInstance result = null;
        for (T value : this.values)
        {
            CoreInstance coreInstance = (toCoreInstance == null) ? (CoreInstance) value : toCoreInstance.apply(value);
            if (key.equals(indexSpec.getIndexKey(coreInstance)))
            {
                if (result != null)
                {
                    throw new IDConflictException(key);
                }
                result = coreInstance;
            }
        }
        return result;
    }

    @Override
    public <K> ListIterable<CoreInstance> getValuesByIndex(IndexSpecification<K> indexSpec, K key)
    {
        return getValuesByIndex(indexSpec, key, null);
    }

    @Override
    public <K> ListIterable<CoreInstance> getValuesByIndex(IndexSpecification<K> indexSpec, K key, Function<? super T, ? extends CoreInstance> toCoreInstance)
    {
        MutableList<CoreInstance> result = Lists.mutable.empty();
        this.values.forEach(v ->
        {
            CoreInstance coreInstance = (toCoreInstance == null) ? (CoreInstance) v : toCoreInstance.apply(v);
            if (key.equals(indexSpec.getIndexKey(coreInstance)))
            {
                result.add(coreInstance);
            }
        });
        return result;
    }

    @Override
    public void setValues(RichIterable<? extends T> values)
    {
        this.values = Lists.immutable.withAll(values);
    }

    @Override
    public void setValue(int offset, T value)
    {
        ImmutableList<T> current;
        MutableList<T> newValues;
        do
        {
            current = this.values;
            newValues = Lists.mutable.withAll(current);
            newValues.set(offset, value);
        }
        while (!UPDATER.compareAndSet(this, current, newValues.toImmutable()));
    }

    @Override
    public void addValue(T value)
    {
        ImmutableList<T> current;
        do
        {
            current = this.values;
        }
        while (!UPDATER.compareAndSet(this, current, current.newWith(value)));
    }

    public void addValues(Iterable<? extends T> values)
    {
        ImmutableList<T> current;
        do
        {
            current = this.values;
        }
        while (!UPDATER.compareAndSet(this, current, current.newWithAll(values)));
    }

    @Override
    public boolean removeValue(Object value)
    {
        if (value != null)
        {
            ImmutableList<T> current;
            int index;
            while ((index = (current = this.values).indexOf(value)) >= 0)
            {
                if (UPDATER.compareAndSet(this, current, current.newWithout(current.get(index))))
                {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void removeAllValues()
    {
        this.values = Lists.immutable.empty();
    }

    @Override
    public ManyValues<T> copy()
    {
        return new ManyValues<>(this.values);
    }

    public static <V> ManyValues<V> fromValues(ListIterable<? extends V> propertyValues)
    {
        return new ManyValues<>((propertyValues == null) ? Lists.immutable.empty() : Lists.immutable.withAll(propertyValues));
    }

    public static <V> ManyValues<V> fromSuppliers(ListIterable<? extends Supplier<? extends V>> suppliers)
    {
        return new ManyValues<>(((suppliers == null) || suppliers.isEmpty()) ? Lists.immutable.empty() : new LazyImmutableList<>(suppliers));
    }

    private abstract static class AbstractLazyImmutableList<T> extends AbstractImmutableCollection<T> implements ImmutableList<T>, List<T>, RandomAccess
    {
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

        @SuppressWarnings("unchecked")
        @Override
        public ImmutableList<T> newWith(T element)
        {
            int size = size();
            Object[] newItems = new Object[size + 1];
            boolean anySupplier = false;
            for (int i = 0; i < size; i++)
            {
                Object item = getRaw(i);
                if (item.getClass() == SharedSupplier.class)
                {
                    SharedSupplier<?> supplier = (SharedSupplier<?>) item;
                    if (supplier.isResolved())
                    {
                        Object value = supplier.getResolvedValue();
                        compareAndSet(i, item, value);
                        newItems[i] = value;
                    }
                    else
                    {
                        anySupplier = true;
                        newItems[i] = item;
                    }
                }
                else
                {
                    newItems[i] = item;
                }
            }
            newItems[size] = element;
            return anySupplier ?
                   new LazyImmutableList<>(newItems) :
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
                newItems[i] = getRaw(i);
            }
            boolean anySupplier = false;
            for (int i = index + 1; i < size; i++)
            {
                Object item = getRaw(i);
                if (item.getClass() == SharedSupplier.class)
                {
                    SharedSupplier<?> supplier = (SharedSupplier<?>) item;
                    if (supplier.isResolved())
                    {
                        Object value = supplier.getResolvedValue();
                        compareAndSet(i, item, value);
                        newItems[i - 1] = value;
                    }
                    else
                    {
                        anySupplier = true;
                        newItems[i - 1] = item;
                    }
                }
                else
                {
                    newItems[i - 1] = item;
                }
            }
            return anySupplier ?
                   new LazyImmutableList<>(newItems) :
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
            boolean anySupplier = false;
            int i = 0;
            while (i < oldSize)
            {
                Object item = getRaw(i);
                if (item.getClass() == SharedSupplier.class)
                {
                    SharedSupplier<?> supplier = (SharedSupplier<?>) item;
                    if (supplier.isResolved())
                    {
                        Object value = supplier.getResolvedValue();
                        compareAndSet(i, item, value);
                        newItems[i] = value;
                    }
                    else
                    {
                        anySupplier = true;
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
            return anySupplier ?
                   new LazyImmutableList<>(newItems) :
                   Lists.immutable.with((T[]) newItems);
        }

        @Override
        public ImmutableList<T> newWithoutAll(Iterable<? extends T> elements)
        {
            Set<? extends T> toRemove = (elements instanceof Set) ? (Set<? extends T>) elements : Sets.mutable.withAll(elements);
            return (detectIndex(toRemove::contains) == -1) ? this : reject(toRemove::contains);
        }

        @Override
        public T get(int index)
        {
            return getChecked(checkBounds(index));
        }

        @SuppressWarnings("unchecked")
        private T getChecked(int index)
        {
            Object item = getRaw(index);
            if (item.getClass() == SharedSupplier.class)
            {
                T value = ((SharedSupplier<? extends T>) item).get();
                compareAndSet(index, item, value);
                return value;
            }
            return (T) item;
        }

        @Override
        public int indexOf(Object object)
        {
            for (int i = 0, size = size(); i < size; i++)
            {
                if (Objects.equals(getChecked(i), object))
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
                if (Objects.equals(getChecked(i), o))
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
        public void each(Procedure<? super T> procedure)
        {
            for (int i = 0, size = size(); i < size; i++)
            {
                procedure.value(getChecked(i));
            }
        }

        @Override
        public void forEach(int startIndex, int endIndex, Procedure<? super T> procedure)
        {
            ListIterate.rangeCheck(startIndex, endIndex, size());
            for (int i = startIndex; i < endIndex; i++)
            {
                procedure.value(getChecked(i));
            }
        }

        @Override
        public void forEachWithIndex(int fromIndex, int toIndex, ObjectIntProcedure<? super T> procedure)
        {
            ListIterate.rangeCheck(fromIndex, toIndex, size());
            for (int i = fromIndex; i < toIndex; i++)
            {
                procedure.value(getChecked(i), i);
            }
        }

        @Override
        public ImmutableList<T> tap(Procedure<? super T> procedure)
        {
            each(procedure);
            return this;
        }

        @Override
        public ImmutableList<T> select(Predicate<? super T> predicate)
        {
            return ListIterate.select(this, predicate, Lists.mutable.empty()).toImmutable();
        }

        @Override
        public <P> ImmutableList<T> selectWith(Predicate2<? super T, ? super P> predicate, P parameter)
        {
            return ListIterate.selectWith(this, predicate, parameter, Lists.mutable.empty()).toImmutable();
        }

        @Override
        public ImmutableList<T> reject(Predicate<? super T> predicate)
        {
            return ListIterate.reject(this, predicate, Lists.mutable.empty()).toImmutable();
        }

        @Override
        public <P> ImmutableList<T> rejectWith(Predicate2<? super T, ? super P> predicate, P parameter)
        {
            return ListIterate.rejectWith(this, predicate, parameter, Lists.mutable.empty()).toImmutable();
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
        public <V> ImmutableList<V> collect(org.eclipse.collections.api.block.function.Function<? super T, ? extends V> function)
        {
            return ListIterate.collect(this, function, Lists.mutable.<V>ofInitialCapacity(size())).toImmutable();
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
        public int detectIndex(Predicate<? super T> predicate)
        {
            return ListIterate.detectIndex(this, predicate);
        }

        @Override
        public <P, V> ImmutableList<V> collectWith(Function2<? super T, ? super P, ? extends V> function, P parameter)
        {
            return ListIterate.collectWith(this, function, parameter, Lists.mutable.<V>ofInitialCapacity(size())).toImmutable();
        }

        @Override
        public <V> ImmutableList<V> collectIf(Predicate<? super T> predicate, org.eclipse.collections.api.block.function.Function<? super T, ? extends V> function)
        {
            return ListIterate.collectIf(this, predicate, function, Lists.mutable.<V>empty()).toImmutable();
        }

        @Override
        public <V> ImmutableList<V> flatCollect(org.eclipse.collections.api.block.function.Function<? super T, ? extends Iterable<V>> function)
        {
            return ListIterate.flatCollect(this, function).toImmutable();
        }

        @Override
        public <V> ImmutableListMultimap<V, T> groupBy(org.eclipse.collections.api.block.function.Function<? super T, ? extends V> function)
        {
            return ListIterate.groupBy(this, function, Multimaps.mutable.list.<V, T>empty()).toImmutable();
        }

        @Override
        public <V> ImmutableListMultimap<V, T> groupByEach(org.eclipse.collections.api.block.function.Function<? super T, ? extends Iterable<V>> function)
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
        public <V> ImmutableList<T> distinctBy(org.eclipse.collections.api.block.function.Function<? super T, ? extends V> function)
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
            return subList(0, count);
        }

        @Override
        public ImmutableList<T> takeWhile(Predicate<? super T> predicate)
        {
            return ListIterate.takeWhile(this, predicate).toImmutable();
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
            return ListIterate.dropWhile(this, predicate).toImmutable();
        }

        @Override
        public PartitionImmutableList<T> partitionWhile(Predicate<? super T> predicate)
        {
            return ListIterate.partitionWhile(this, predicate).toImmutable();
        }

        @Override
        public List<T> castToList()
        {
            return this;
        }

        @Override
        public abstract LazyImmutableSubList<T> subList(int fromIndex, int toIndex);

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
            boolean anySupplier = false;
            for (int i = 0, j = size - 1; i < size; i++, j--)
            {
                Object item = getRaw(i);
                if (item.getClass() == SharedSupplier.class)
                {
                    SharedSupplier<?> supplier = (SharedSupplier<?>) item;
                    if (supplier.isResolved())
                    {
                        Object value = supplier.getResolvedValue();
                        compareAndSet(i, item, value);
                        reversed[j] = value;
                    }
                    else
                    {
                        anySupplier = true;
                        reversed[j] = item;
                    }
                }
                else
                {
                    reversed[j] = item;
                }
            }
            return anySupplier ?
                   new LazyImmutableList<>(reversed) :
                   Lists.immutable.with((T[]) reversed);
        }

        @Override
        public int detectLastIndex(Predicate<? super T> predicate)
        {
            return ListIterate.detectLastIndex(this, predicate);
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

        @Override
        protected MutableCollection<T> newMutable(int size)
        {
            return Lists.mutable.ofInitialCapacity(size);
        }

        protected abstract Object getRaw(int index);

        protected abstract void compareAndSet(int index, Object expectedValue, Object newValue);

        private int checkBounds(int index)
        {
            if ((index < 0) || (index >= size()))
            {
                throw new IndexOutOfBoundsException("Index: " + index + " Size: " + size());
            }
            return index;
        }
    }

    static class LazyImmutableList<T> extends AbstractLazyImmutableList<T>
    {
        private final AtomicReferenceArray<Object> items;

        private LazyImmutableList(AtomicReferenceArray<Object> items)
        {
            this.items = items;
        }

        private LazyImmutableList(Object... items)
        {
            this(new AtomicReferenceArray<>(items));
        }

        private LazyImmutableList(ListIterable<? extends Supplier<? extends T>> suppliers)
        {
            this.items = new AtomicReferenceArray<>(suppliers.size());
            suppliers.forEachWithIndex((supplier, i) -> this.items.set(i, new SharedSupplier<>(supplier)));
        }

        @Override
        public int size()
        {
            return this.items.length();
        }

        @Override
        public LazyImmutableSubList<T> subList(int fromIndex, int toIndex)
        {
            int size = size();
            if (fromIndex < 0)
            {
                throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
            }
            if (toIndex > size)
            {
                throw new IndexOutOfBoundsException("toIndex = " + toIndex);
            }
            if (fromIndex > toIndex)
            {
                throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ')');
            }
            return new LazyImmutableSubList<>(this.items, fromIndex, toIndex);
        }

        @Override
        protected Object getRaw(int index)
        {
            return this.items.get(index);
        }

        @Override
        protected void compareAndSet(int index, Object expectedValue, Object newValue)
        {
            this.items.compareAndSet(index, expectedValue, newValue);
        }
    }

    static class LazyImmutableSubList<T> extends AbstractLazyImmutableList<T>
    {
        private final AtomicReferenceArray<Object> items;
        private final int offset;
        private final int size;

        private LazyImmutableSubList(AtomicReferenceArray<Object> items, int fromIndex, int toIndex)
        {
            this.items = items;
            this.offset = fromIndex;
            this.size = toIndex - fromIndex;
        }

        @Override
        public int size()
        {
            return this.size;
        }

        @Override
        public LazyImmutableSubList<T> subList(int fromIndex, int toIndex)
        {
            if (fromIndex < 0)
            {
                throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
            }
            if (toIndex > this.size)
            {
                throw new IndexOutOfBoundsException("toIndex = " + toIndex);
            }
            if (fromIndex > toIndex)
            {
                throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ')');
            }
            return new LazyImmutableSubList<>(this.items, fromIndex + this.offset, toIndex + this.offset);
        }

        @Override
        protected Object getRaw(int index)
        {
            return this.items.get(index + this.offset);
        }

        @Override
        protected void compareAndSet(int index, Object expectedValue, Object newValue)
        {
            this.items.compareAndSet(index + this.offset, expectedValue, newValue);
        }
    }
}

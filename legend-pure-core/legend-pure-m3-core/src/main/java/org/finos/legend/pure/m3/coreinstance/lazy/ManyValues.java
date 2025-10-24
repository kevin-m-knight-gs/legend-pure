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
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.indexing.IDConflictException;
import org.finos.legend.pure.m4.coreinstance.indexing.IndexSpecification;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Supplier;

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
        return new ManyValues<>(((suppliers == null) || suppliers.isEmpty()) ? Lists.immutable.empty() : LazyResolutionImmutableList.newList(suppliers));
    }
}

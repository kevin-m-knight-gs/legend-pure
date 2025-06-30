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

public abstract class ManyValues<T> implements PropertyValue<T>
{
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<ManyValues, ImmutableList> UPDATER = AtomicReferenceFieldUpdater.newUpdater(ManyValues.class, ImmutableList.class, "values");

    volatile ImmutableList<T> values;

    private ManyValues(ImmutableList<T> values)
    {
        this.values = values;
    }

    private ManyValues()
    {
        this(null);
    }

    @Override
    public boolean isMany()
    {
        return true;
    }

    @Override
    public T getValue()
    {
        ListIterable<T> values = getValues();
        switch (values.size())
        {
            case 0:
            {
                return null;
            }
            case 1:
            {
                return values.get(0);
            }
            default:
            {
                throw new IllegalStateException("Expected at most 1 value, found " + values.size());
            }
        }
    }

    @Override
    public ListIterable<T> getValues()
    {
        init();
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
        for (T value : getValues())
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
        getValues().forEach(v ->
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
        setValues(Lists.immutable.withAll(values));
    }

    @Override
    public void setValue(int offset, T value)
    {
        init();
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
        init();
        ImmutableList<T> current;
        do
        {
            current = this.values;
        }
        while (!UPDATER.compareAndSet(this, current, current.newWith(value)));
    }

    public void addValues(Iterable<? extends T> values)
    {
        init();
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
            init();
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
        setValues(Lists.immutable.empty());
    }

    @Override
    public abstract ManyValues<T> copy();

    protected abstract void init();

    protected abstract void setValues(ImmutableList<T> values);

    public static <V> ManyValues<V> fromValues(ListIterable<? extends V> propertyValues)
    {
        return new SimpleManyValues<>((propertyValues == null) ? Lists.immutable.empty() : Lists.immutable.withAll(propertyValues));
    }

    public static <V> ManyValues<V> fromSuppliers(ListIterable<? extends Supplier<? extends V>> suppliers)
    {
        return ((suppliers == null) || suppliers.isEmpty()) ? new SimpleManyValues<>(Lists.immutable.empty()) : new LazyManyValues<>(suppliers);
    }

    private static class SimpleManyValues<T> extends ManyValues<T>
    {
        private SimpleManyValues(ImmutableList<T> values)
        {
            super(values);
        }

        @Override
        public int size()
        {
            return this.values.size();
        }

        @Override
        public boolean hasValue()
        {
            return this.values.notEmpty();
        }

        @Override
        protected void init()
        {
            // nothing to do
        }

        @Override
        protected void setValues(ImmutableList<T> values)
        {
            this.values = values;
        }


        @Override
        public ManyValues<T> copy()
        {
            return new SimpleManyValues<>(this.values);
        }
    }

    private static class LazyManyValues<T> extends ManyValues<T>
    {
        private ListIterable<? extends Supplier<? extends T>> initializers;

        private LazyManyValues(ListIterable<? extends Supplier<? extends T>> initializers)
        {
            this.initializers = initializers;
        }

        @Override
        public int size()
        {
            ImmutableList<T> local = this.values;
            if (local == null)
            {
                synchronized (this)
                {
                    if ((local = this.values) == null)
                    {
                        return this.initializers.size();
                    }
                }
            }
            return local.size();
        }

        @Override
        public boolean hasValue()
        {
            ImmutableList<T> local = this.values;
            if (local == null)
            {
                synchronized (this)
                {
                    if ((local = this.values) == null)
                    {
                        return this.initializers.notEmpty();
                    }
                }
            }
            return local.notEmpty();
        }

        @Override
        protected void init()
        {
            if (this.values == null)
            {
                synchronized (this)
                {
                    if (this.values == null)
                    {
                        this.values = this.initializers.collect(Supplier::get, Lists.mutable.<T>ofInitialCapacity(this.initializers.size())).toImmutable();
                        this.initializers = null;
                    }
                }
            }
        }

        @Override
        protected void setValues(ImmutableList<T> values)
        {
            if (this.values == null)
            {
                synchronized (this)
                {
                    this.values = values;
                    this.initializers = null;
                }
            }
            else
            {
                this.values = values;
            }
        }

        @Override
        public ManyValues<T> copy()
        {
            ImmutableList<T> local = this.values;
            if (local == null)
            {
                synchronized (this)
                {
                    if ((local = this.values) == null)
                    {
                        if (this.initializers.isEmpty())
                        {
                            return new SimpleManyValues<>(Lists.immutable.empty());
                        }
                        if (!(this.initializers.get(0) instanceof SharedSupplier))
                        {
                            this.initializers = this.initializers.collect(SharedSupplier::new, Lists.mutable.<Supplier<? extends T>>ofInitialCapacity(this.initializers.size()));
                        }
                        else if (((SharedSupplier<?>) this.initializers.get(0)).isResolved())
                        {
                            // The SharedSupplier(s) might have been resolved (or might be in the process of being
                            // resolved) by another instance holding it. Note that if one is resolved, then all are
                            // resolved (or being resolved). In that case, we can resolve the values for this holder,
                            // as well as the copy.
                            this.values = local = this.initializers.collect(Supplier::get, Lists.mutable.<T>ofInitialCapacity(this.initializers.size())).toImmutable();
                            this.initializers = null;
                            return new SimpleManyValues<>(local);
                        }
                        return new LazyManyValues<>(this.initializers);
                    }
                }
            }
            return new SimpleManyValues<>(local);
        }
    }
}

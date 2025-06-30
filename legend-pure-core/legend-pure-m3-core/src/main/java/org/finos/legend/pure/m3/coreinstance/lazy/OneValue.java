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
import org.eclipse.collections.api.list.ListIterable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.indexing.IndexSpecification;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class OneValue<T> implements PropertyValue<T>
{
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<OneValue, Object> UPDATER = AtomicReferenceFieldUpdater.newUpdater(OneValue.class, Object.class, "value");

    protected volatile T value;

    private OneValue()
    {
    }

    @Override
    public boolean isMany()
    {
        return false;
    }

    @Override
    public boolean hasValue()
    {
        return getValue() != null;
    }

    @Override
    public int size()
    {
        return hasValue() ? 1 : 0;
    }

    @Override
    public T getValue()
    {
        init();
        return this.value;
    }

    @Override
    public ListIterable<T> getValues()
    {
        T local = getValue();
        return (local == null) ? Lists.immutable.empty() : Lists.immutable.with(local);
    }

    @Override
    public <K> CoreInstance getValueByIDIndex(IndexSpecification<K> indexSpec, K key)
    {
        return getValueByIDIndex(indexSpec, key, null);
    }

    public <K> CoreInstance getValueByIDIndex(IndexSpecification<K> indexSpec, K key, Function<? super T, ? extends CoreInstance> toCoreInstance)
    {
        T local = getValue();
        if (local != null)
        {
            CoreInstance coreInstance = (toCoreInstance == null) ? (CoreInstance) local : toCoreInstance.apply(local);
            if (key.equals(indexSpec.getIndexKey(coreInstance)))
            {
                return coreInstance;
            }
        }
        return null;
    }

    @Override
    public <K> ListIterable<CoreInstance> getValuesByIndex(IndexSpecification<K> indexSpec, K key)
    {
        return getValuesByIndex(indexSpec, key, null);
    }

    @Override
    public <K> ListIterable<CoreInstance> getValuesByIndex(IndexSpecification<K> indexSpec, K key, Function<? super T, ? extends CoreInstance> toCoreInstance)
    {
        T local = getValue();
        if (local != null)
        {
            CoreInstance coreInstance = (toCoreInstance == null) ? (CoreInstance) local : toCoreInstance.apply(local);
            if (key.equals(indexSpec.getIndexKey(coreInstance)))
            {
                return Lists.immutable.with(coreInstance);
            }
        }
        return Lists.immutable.empty();
    }

    public abstract void setValue(T newValue);

    @Override
    public void setValues(RichIterable<? extends T> values)
    {
        switch (values.size())
        {
            case 0:
            {
                setValue(null);
                return;
            }
            case 1:
            {
                setValue(values.getAny());
                return;
            }
            default:
            {
                throw new IllegalArgumentException("Cannot set multiple values for a to-one property: " + values.size() + " values provided");
            }
        }
    }

    @Override
    public void setValue(int offset, T value)
    {
        if (offset != 0)
        {
            throw new IllegalArgumentException("Cannot modify value at offset " + offset + " for to-one property");
        }
        setValue(value);
    }

    @Override
    public void addValue(T value)
    {
        init();
        if (!UPDATER.compareAndSet(this, null, value))
        {
            throw new IllegalStateException("Cannot add value to to-one property: value already present");
        }
    }

    @Override
    public boolean removeValue(Object value)
    {
        if (value != null)
        {
            init();
            T currentValue;
            while (((currentValue = this.value) != null) && currentValue.equals(value))
            {
                if (UPDATER.compareAndSet(this, currentValue, null))
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
        setValue(null);
    }

    @Override
    public abstract OneValue<T> copy();

    abstract void init();

    public static <V> OneValue<V> fromValue(V value)
    {
        return new SimpleOneValue<>(value);
    }

    public static <V> OneValue<V> fromSupplier(Supplier<? extends V> supplier)
    {
        return (supplier == null) ? new SimpleOneValue<>(null) : new LazyOneValue<>(supplier);
    }

    private static class SimpleOneValue<T> extends OneValue<T>
    {
        private SimpleOneValue(T value)
        {
            this.value = value;
        }

        @Override
        public void setValue(T newValue)
        {
            this.value = newValue;
        }

        @Override
        void init()
        {
            // nothing to do
        }

        @Override
        public OneValue<T> copy()
        {
            return new SimpleOneValue<>(this.value);
        }
    }

    private static class LazyOneValue<T> extends OneValue<T>
    {
        private volatile Supplier<? extends T> initializer;

        private LazyOneValue(Supplier<? extends T> initializer)
        {
            this.initializer = initializer;
        }

        @Override
        public void setValue(T newValue)
        {
            if (this.initializer == null)
            {
                this.value = newValue;
            }
            else
            {
                synchronized (this)
                {
                    this.value = newValue;
                    this.initializer = null;
                }
            }
        }

        @Override
        void init()
        {
            if (this.initializer != null)
            {
                synchronized (this)
                {
                    Supplier<? extends T> local = this.initializer;
                    if (local != null)
                    {
                        this.value = local.get();
                        this.initializer = null;
                    }
                }
            }
        }

        @Override
        public OneValue<T> copy()
        {
            if (this.initializer != null)
            {
                synchronized (this)
                {
                    Supplier<? extends T> local = this.initializer;
                    if (local != null)
                    {
                        if (local instanceof SharedSupplier)
                        {
                            SharedSupplier<? extends T> sharedSupplier = (SharedSupplier<? extends T>) local;
                            if (sharedSupplier.isResolved())
                            {
                                // The SharedSupplier might have been resolved by another instance holding it.
                                // In that case, we can resolve the value for this holder, as well as the copy.
                                T v = this.value = sharedSupplier.getResolvedValue();
                                this.initializer = null;
                                return new SimpleOneValue<>(v);
                            }
                        }
                        else
                        {
                            this.initializer = local = new SharedSupplier<>(local);
                        }
                        return new LazyOneValue<>(local);
                    }
                }
            }
            return new SimpleOneValue<>(this.value);
        }
    }
}

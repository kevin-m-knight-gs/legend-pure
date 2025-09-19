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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.serialization.compiler.element.ElementBuilder;
import org.finos.legend.pure.m3.serialization.compiler.element.InstanceData;
import org.finos.legend.pure.m3.serialization.compiler.element.PropertyValues;
import org.finos.legend.pure.m3.serialization.compiler.element.Reference;
import org.finos.legend.pure.m3.serialization.compiler.element.Value;
import org.finos.legend.pure.m3.serialization.compiler.element.ValueOrReference;
import org.finos.legend.pure.m3.serialization.compiler.element.ValueOrReferenceVisitor;
import org.finos.legend.pure.m3.serialization.compiler.metadata.BackReference;
import org.finos.legend.pure.m3.serialization.compiler.metadata.BackReferenceConsumer;
import org.finos.legend.pure.m3.serialization.compiler.metadata.MetadataIndex;
import org.finos.legend.pure.m3.serialization.compiler.metadata.PackageableElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolver;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolvers;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.AbstractCoreInstance;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.coreinstance.compileState.CompileState;
import org.finos.legend.pure.m4.coreinstance.compileState.CompileStateSet;
import org.finos.legend.pure.m4.coreinstance.indexing.IDConflictException;
import org.finos.legend.pure.m4.coreinstance.indexing.IndexSpecification;
import org.finos.legend.pure.m4.coreinstance.primitive.date.PureDate;
import org.finos.legend.pure.m4.coreinstance.primitive.strictTime.PureStrictTime;
import org.finos.legend.pure.m4.exception.PureCompilationException;
import org.finos.legend.pure.m4.transaction.ModelRepositoryTransaction;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public abstract class AbstractLazyCoreInstance extends AbstractCoreInstance
{
    private static final AtomicIntegerFieldUpdater<AbstractLazyCoreInstance> UPDATER = AtomicIntegerFieldUpdater.newUpdater(AbstractLazyCoreInstance.class, "compileStateBitSet");

    private volatile int compileStateBitSet;

    protected final ModelRepository repository;
    protected final int internalSyntheticId;
    protected volatile String name;
    protected volatile SourceInformation sourceInformation;
    protected final OneValue<CoreInstance> classifier;

    private AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, OneValue<CoreInstance> classifier)
    {
        this.repository = repository;
        this.internalSyntheticId = internalSyntheticId;
        this.name = name;
        this.sourceInformation = sourceInformation;
        this.compileStateBitSet = compileStateBitSet;
        this.classifier = classifier;
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, fromValue(null));
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, CoreInstance classifier)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, fromValue(classifier));
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, Supplier<? extends CoreInstance> classifierSupplier)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, fromSupplier(classifierSupplier));
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, String classifierPath, Function<? super String, ? extends CoreInstance> packagePathResolver)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, packageableElementSupplier(packagePathResolver, classifierPath));
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, String classifierPath, ReferenceIdResolvers referenceIdResolvers)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, classifierPath, referenceIdResolvers.packagePathResolver());
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, String classifierPath, ReferenceIdResolver referenceIdResolver)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, packageableElementSupplier(referenceIdResolver, classifierPath));
    }

    protected AbstractLazyCoreInstance(AbstractLazyCoreInstance source)
    {
        this(source.repository, -1, source.name, source.sourceInformation, source.compileStateBitSet, source.classifier.copy());
    }

    @Override
    public ModelRepository getRepository()
    {
        return this.repository;
    }

    @Override
    public int getSyntheticId()
    {
        return this.internalSyntheticId;
    }

    @Override
    public String getName()
    {
        return this.name;
    }

    @Override
    public void setName(String name)
    {
        this.name = name;
    }

    @Override
    public SourceInformation getSourceInformation()
    {
        return this.sourceInformation;
    }

    @Override
    public void setSourceInformation(SourceInformation sourceInformation)
    {
        this.sourceInformation = sourceInformation;
    }

    @Override
    public CoreInstance getClassifier()
    {
        return this.classifier.getValue();
    }

    @Override
    public void setClassifier(CoreInstance classifier)
    {
        this.classifier.setValue(classifier);
    }

    @Override
    public boolean isPersistent()
    {
        return true;
    }

    @Override
    public void addKeyWithEmptyList(ListIterable<String> key)
    {
        removeProperty(key.getLast());
    }

    @Override
    public CoreInstance getKeyByName(String name)
    {
        ListIterable<String> realKey = getRealKeyByName(name);
        if (realKey == null)
        {
            throw new IllegalArgumentException("Unsupported key: " + name);
        }
        return resolveRealKey(realKey);
    }

    protected CoreInstance resolveRealKey(ListIterable<String> realKey)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public <K> CoreInstance getValueInValueForMetaPropertyToManyByIDIndex(String keyName, IndexSpecification<K> indexSpec, K keyInIndex)
    {
        try
        {
            return getValueByIDIndex(keyName, indexSpec, keyInIndex);
        }
        catch (IDConflictException e)
        {
            StringBuilder builder = new StringBuilder("Invalid ID index for property '").append(keyName).append("' on ").append(this);
            if (this.sourceInformation != null)
            {
                this.sourceInformation.appendMessage(builder.append(" (")).append(')');
            }
            builder.append(": multiple values for id ").append(e.getId());
            throw new RuntimeException(builder.toString(), e);
        }
    }

    protected abstract <K> CoreInstance getValueByIDIndex(String keyName, IndexSpecification<K> indexSpec, K keyInIndex) throws IDConflictException;

    @Override
    public void commit(ModelRepositoryTransaction transaction)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasCompileState(CompileState state)
    {
        return CompileStateSet.bitSetHasCompileState(this.compileStateBitSet, state);
    }

    @Override
    public void addCompileState(CompileState state)
    {
        int currentState;
        do
        {
            currentState = this.compileStateBitSet;
        }
        while (!UPDATER.compareAndSet(this, currentState, CompileStateSet.addCompileStateToBitSet(currentState, state)));
    }

    @Override
    public void removeCompileState(CompileState state)
    {
        int currentState;
        do
        {
            currentState = this.compileStateBitSet;
        }
        while (!UPDATER.compareAndSet(this, currentState, CompileStateSet.removeCompileStateFromBitSet(currentState, state)));
    }

    @Override
    public CompileStateSet getCompileStates()
    {
        return CompileStateSet.fromBitSet(this.compileStateBitSet);
    }

    @Override
    public void setCompileStatesFrom(CompileStateSet states)
    {
        setCompileStatesFrom(states.toBitSet());
    }

    protected void setCompileStatesFrom(int states)
    {
        this.compileStateBitSet = states;
    }

    @Override
    public void validate(MutableSet<CoreInstance> doneList) throws PureCompilationException
    {
        throw new UnsupportedOperationException();
    }

    protected static MutableMap<String, PropertyValues> indexPropertyValues(InstanceData instanceData)
    {
        ListIterable<? extends PropertyValues> propertyValues = instanceData.getPropertyValues();
        MutableMap<String, PropertyValues> index = Maps.mutable.ofInitialCapacity(propertyValues.size());
        propertyValues.forEach(pv ->
        {
            PropertyValues old = index.put(pv.getPropertyName(), pv);
            if (old != null)
            {
                StringBuilder builder = new StringBuilder("Property value conflict for property '").append(pv.getPropertyName()).append("' for ");
                String id = instanceData.getReferenceId();
                if (id != null)
                {
                    builder.append(id).append(' ');
                }
                builder.append("instance of ").append(instanceData.getClassifierPath());
                SourceInformation sourceInfo = instanceData.getSourceInformation();
                if (sourceInfo != null)
                {
                    sourceInfo.appendMessage(builder.append(" at "));
                }
                throw new RuntimeException(builder.toString());
            }
        });
        return index;
    }

    protected static <V> OneValue<V> newToOnePropertyValue(PropertyValues propertyValues, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver, boolean allowEagerValueResolution)
    {
        if (propertyValues == null)
        {
            return fromValue(null);
        }
        ListIterable<ValueOrReference> values = propertyValues.getValues();
        if (values.size() > 1)
        {
            throw new IllegalStateException("Cannot create to-one property value for property '" + propertyValues.getPropertyName() + "': " + values.size() + " values present");
        }
        return newToOnePropertyValue(values.getFirst(), extResolver, intResolver, primitiveValueResolver, allowEagerValueResolution);
    }

    @SuppressWarnings("unchecked")
    protected static <V> OneValue<V> newToOnePropertyValue(ValueOrReference propertyValue, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver, boolean allowEagerValueResolution)
    {
        return (propertyValue == null) ? fromValue(null) : propertyValue.visit(new ValueOrReferenceVisitor<OneValue<V>>()
        {
            @Override
            public OneValue<V> visit(Reference.ExternalReference reference)
            {
                String id = reference.getId();
                return fromSupplier(() -> (V) extResolver.resolveReference(id));
            }

            @Override
            public OneValue<V> visit(Reference.InternalReference reference)
            {
                int id = reference.getId();
                return fromSupplier(() -> (V) intResolver.apply(id));
            }

            @Override
            public OneValue<V> visit(Value.BooleanValue value)
            {
                boolean b = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveBoolean(b)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveBoolean(b));
            }

            @Override
            public OneValue<V> visit(Value.ByteValue value)
            {
                byte b = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveByte(b)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveByte(b));
            }

            @Override
            public OneValue<V> visit(Value.DateValue value)
            {
                PureDate date = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveDate(date)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveDate(date));
            }

            @Override
            public OneValue<V> visit(Value.DateTimeValue value)
            {
                PureDate dateTime = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveDateTime(dateTime)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveDateTime(dateTime));
            }

            @Override
            public OneValue<V> visit(Value.StrictDateValue value)
            {
                PureDate strictDate = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveStrictDate(strictDate)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveStrictDate(strictDate));
            }

            @Override
            public OneValue<V> visit(Value.LatestDateValue value)
            {
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveLatestDate()) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveLatestDate());
            }

            @Override
            public OneValue<V> visit(Value.DecimalValue value)
            {
                BigDecimal d = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveDecimal(d)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveDecimal(d));
            }

            @Override
            public OneValue<V> visit(Value.FloatValue value)
            {
                BigDecimal f = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveFloat(f)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveFloat(f));
            }

            @Override
            public OneValue<V> visit(Value.IntegerValue value)
            {
                Number i = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveInteger(i)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveInteger(i));
            }

            @Override
            public OneValue<V> visit(Value.StrictTimeValue value)
            {
                PureStrictTime strictTime = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveStrictTime(strictTime)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveStrictTime(strictTime));
            }

            @Override
            public OneValue<V> visit(Value.StringValue value)
            {
                String string = value.getValue();
                return allowEagerValueResolution ?
                       fromValue((V) primitiveValueResolver.resolveString(string)) :
                       fromSupplier(() -> (V) primitiveValueResolver.resolveString(string));
            }
        });
    }

    protected static <V> ManyValues<V> newToManyPropertyValue(PropertyValues propertyValues, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver, boolean allowEagerValueResolution)
    {
        return newToManyPropertyValue(propertyValues, extResolver, intResolver, primitiveValueResolver, allowEagerValueResolution, null);
    }

    protected static <V> ManyValues<V> newToManyPropertyValue(PropertyValues propertyValues, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver, boolean allowEagerValueResolution, ListIterable<? extends Supplier<? extends V>> extraSuppliers)
    {
        ListIterable<ValueOrReference> pvList = (propertyValues == null) ? null : propertyValues.getValues();
        if ((extraSuppliers != null) && extraSuppliers.notEmpty())
        {
            if ((pvList == null) || pvList.isEmpty())
            {
                return fromSuppliers(extraSuppliers.toImmutable());
            }
            ValueOrReferenceVisitor<Supplier<V>> visitor = getSupplierVisitor(extResolver, intResolver, primitiveValueResolver, allowEagerValueResolution);
            MutableList<Supplier<? extends V>> suppliers = Lists.mutable.ofInitialCapacity(pvList.size() + extraSuppliers.size());
            pvList.collect(v -> v.visit(visitor), suppliers);
            suppliers.addAllIterable(extraSuppliers);
            return fromSuppliers(suppliers.toImmutable());
        }
        if ((pvList == null) || pvList.isEmpty())
        {
            return fromValues(null);
        }
        if (allowEagerValueResolution && pvList.noneSatisfy(v -> v instanceof Reference))
        {
            ValueOrReferenceVisitor<V> visitor = getValueVisitor(primitiveValueResolver);
            return fromValues(pvList.collect(v -> v.visit(visitor), Lists.mutable.ofInitialCapacity(pvList.size())));
        }
        ValueOrReferenceVisitor<Supplier<V>> visitor = getSupplierVisitor(extResolver, intResolver, primitiveValueResolver, allowEagerValueResolution);
        return fromSuppliers(pvList.collect(v -> v.visit(visitor), Lists.mutable.ofInitialCapacity(pvList.size())));
    }

    @SuppressWarnings("unchecked")
    private static <V> ValueOrReferenceVisitor<V> getValueVisitor(PrimitiveValueResolver primitiveValueResolver)
    {
        return new ValueOrReferenceVisitor<V>()
        {
            @Override
            public V visit(Value.BooleanValue value)
            {
                return (V) primitiveValueResolver.resolveBoolean(value.getValue());
            }

            @Override
            public V visit(Value.ByteValue value)
            {
                return (V) primitiveValueResolver.resolveByte(value.getValue());
            }

            @Override
            public V visit(Value.DateValue value)
            {
                return (V) primitiveValueResolver.resolveDate(value.getValue());
            }

            @Override
            public V visit(Value.DateTimeValue value)
            {
                return (V) primitiveValueResolver.resolveDateTime(value.getValue());
            }

            @Override
            public V visit(Value.StrictDateValue value)
            {
                return (V) primitiveValueResolver.resolveStrictDate(value.getValue());
            }

            @Override
            public V visit(Value.LatestDateValue value)
            {
                return (V) primitiveValueResolver.resolveLatestDate();
            }

            @Override
            public V visit(Value.DecimalValue value)
            {
                return (V) primitiveValueResolver.resolveDecimal(value.getValue());
            }

            @Override
            public V visit(Value.FloatValue value)
            {
                return (V) primitiveValueResolver.resolveFloat(value.getValue());
            }

            @Override
            public V visit(Value.IntegerValue value)
            {
                return (V) primitiveValueResolver.resolveInteger(value.getValue());
            }

            @Override
            public V visit(Value.StrictTimeValue value)
            {
                return (V) primitiveValueResolver.resolveStrictTime(value.getValue());
            }

            @Override
            public V visit(Value.StringValue value)
            {
                return (V) primitiveValueResolver.resolveString(value.getValue());
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <V> ValueOrReferenceVisitor<Supplier<V>> getSupplierVisitor(ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver, boolean allowEagerValueResolution)
    {
        return allowEagerValueResolution ?
               new ValueOrReferenceVisitor<Supplier<V>>()
               {
                   @Override
                   public Supplier<V> visit(Reference.ExternalReference reference)
                   {
                       String id = reference.getId();
                       return () -> (V) extResolver.resolveReference(id);
                   }

                   @Override
                   public Supplier<V> visit(Reference.InternalReference reference)
                   {
                       int id = reference.getId();
                       return () -> (V) intResolver.apply(id);
                   }

                   @Override
                   public Supplier<V> visit(Value.BooleanValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveBoolean(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.ByteValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveByte(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.DateValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveDate(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.DateTimeValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveDateTime(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.StrictDateValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveStrictDate(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.LatestDateValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveLatestDate());
                   }

                   @Override
                   public Supplier<V> visit(Value.DecimalValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveDecimal(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.FloatValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveFloat(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.IntegerValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveInteger(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.StrictTimeValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveStrictTime(value.getValue()));
                   }

                   @Override
                   public Supplier<V> visit(Value.StringValue value)
                   {
                       return valueSupplier((V) primitiveValueResolver.resolveString(value.getValue()));
                   }

                   private Supplier<V> valueSupplier(V value)
                   {
                       return () -> value;
                   }
               } :
               new ValueOrReferenceVisitor<Supplier<V>>()
               {
                   @Override
                   public Supplier<V> visit(Reference.ExternalReference reference)
                   {
                       String id = reference.getId();
                       return () -> (V) extResolver.resolveReference(id);
                   }

                   @Override
                   public Supplier<V> visit(Reference.InternalReference reference)
                   {
                       int id = reference.getId();
                       return () -> (V) intResolver.apply(id);
                   }

                   @Override
                   public Supplier<V> visit(Value.BooleanValue value)
                   {
                       boolean b = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveBoolean(b);
                   }

                   @Override
                   public Supplier<V> visit(Value.ByteValue value)
                   {
                       byte b = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveByte(b);
                   }

                   @Override
                   public Supplier<V> visit(Value.DateValue value)
                   {
                       PureDate date = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveDate(date);
                   }

                   @Override
                   public Supplier<V> visit(Value.DateTimeValue value)
                   {
                       PureDate dateTime = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveDateTime(dateTime);
                   }

                   @Override
                   public Supplier<V> visit(Value.StrictDateValue value)
                   {
                       PureDate strictDate = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveStrictDate(strictDate);
                   }

                   @Override
                   public Supplier<V> visit(Value.LatestDateValue value)
                   {
                       return () -> (V) primitiveValueResolver.resolveLatestDate();
                   }

                   @Override
                   public Supplier<V> visit(Value.DecimalValue value)
                   {
                       BigDecimal d = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveDecimal(d);
                   }

                   @Override
                   public Supplier<V> visit(Value.FloatValue value)
                   {
                       BigDecimal f = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveFloat(f);
                   }

                   @Override
                   public Supplier<V> visit(Value.IntegerValue value)
                   {
                       Number i = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveInteger(i);
                   }

                   @Override
                   public Supplier<V> visit(Value.StrictTimeValue value)
                   {
                       PureStrictTime strictTime = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveStrictTime(strictTime);
                   }

                   @Override
                   public Supplier<V> visit(Value.StringValue value)
                   {
                       String string = value.getValue();
                       return () -> (V) primitiveValueResolver.resolveString(string);
                   }
               };
    }

    @SuppressWarnings("unchecked")
    protected static <T extends CoreInstance> Supplier<T> packageableElementSupplier(Function<? super String, ? extends CoreInstance> packagePathResolver, String packagePath)
    {
        Objects.requireNonNull(packagePathResolver);
        Objects.requireNonNull(packagePath);
        return () ->
        {
            T result;
            try
            {
                result = (T) packagePathResolver.apply(packagePath);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error resolving '" + packagePath + "'", e);
            }
            if (result == null)
            {
                throw new RuntimeException("Cannot resolve '" + packagePath + "'");
            }
            return result;
        };
    }

    @SuppressWarnings("unchecked")
    protected static <T extends CoreInstance> Supplier<T> packageableElementSupplier(ReferenceIdResolver refIdResolver, String packagePath)
    {
        Objects.requireNonNull(refIdResolver);
        Objects.requireNonNull(packagePath);
        return () ->
        {
            T result;
            try
            {
                result = (T) refIdResolver.resolvePackagePath(packagePath);
            }
            catch (Exception e)
            {
                throw new RuntimeException("Error resolving '" + packagePath + "'", e);
            }
            if (result == null)
            {
                throw new RuntimeException("Cannot resolve '" + packagePath + "'");
            }
            return result;
        };
    }

    protected static IntFunction<CoreInstance> getVacuousInternalIdResolver()
    {
        return i ->
        {
            throw new IllegalArgumentException("Invalid internal id: " + i);
        };
    }

    protected static String getNameFromPath(String path)
    {
        int lastColon = path.lastIndexOf(':');
        return (lastColon == -1) ? path : path.substring(lastColon + 1);
    }

    protected static String getPackageFromPath(String path)
    {
        if (M3Paths.Root.equals(path) || _Package.SPECIAL_TYPES.contains(path))
        {
            return null;
        }
        int lastColon = path.lastIndexOf(':');
        return (lastColon == -1) ? M3Paths.Root : path.substring(0, lastColon - 1);
    }

    protected static <T extends CoreInstance> OneValue<T> computePackage(String path, ReferenceIdResolvers referenceIds)
    {
        return computePackage(path, referenceIds.packagePathResolver());
    }

    protected static <T extends CoreInstance> OneValue<T> computePackage(String path, Function<? super String, ? extends CoreInstance> packagePathResolver)
    {
        String pkg = getPackageFromPath(path);
        return (pkg == null) ? fromValue(null) : fromSupplier(packageableElementSupplier(packagePathResolver, pkg));
    }

    protected static <T extends CoreInstance> ManyValues<T> computePackageChildren(String path, MetadataIndex metadataIndex, ReferenceIdResolvers referenceIds)
    {
        return computePackageChildren(path, metadataIndex, referenceIds.packagePathResolver());
    }

    protected static <T extends CoreInstance> ManyValues<T> computePackageChildren(String path, MetadataIndex metadataIndex, Function<? super String, ? extends CoreInstance> packagePathResolver)
    {
        ImmutableList<PackageableElementMetadata> children = metadataIndex.getPackageChildren(path);
        return children.isEmpty() ?
               fromValues(null) :
               fromSuppliers(children.collect(child -> packageableElementSupplier(packagePathResolver, child.getPath()), Lists.mutable.ofInitialCapacity(children.size())));
    }

    // Back references

    protected static <FE extends CoreInstance, AE extends CoreInstance, P extends CoreInstance, QP extends CoreInstance, RU extends CoreInstance, G extends CoreInstance>
    void collectBackReferences(ListIterable<? extends BackReference> backReferences,
                               ReferenceIdResolvers referenceIds,
                               ElementBuilder elementBuilder,
                               Collection<? super Supplier<? extends FE>> applications,
                               Collection<? super Supplier<? extends AE>> modelElements,
                               Collection<? super Supplier<? extends P>> propertiesFromAssociations,
                               Collection<? super Supplier<? extends QP>> qualifiedPropertiesFromAssociations,
                               Collection<? super Supplier<? extends RU>> refUsages,
                               Collection<? super Supplier<? extends G>> specializations)
    {
        collectBackReferences(backReferences, referenceIds.resolver(), null, elementBuilder, applications, modelElements, propertiesFromAssociations, qualifiedPropertiesFromAssociations, refUsages, specializations);
    }

    protected static <FE extends CoreInstance, AE extends CoreInstance, P extends CoreInstance, QP extends CoreInstance, RU extends CoreInstance, G extends CoreInstance>
    void collectBackReferences(ListIterable<? extends BackReference> backReferences,
                               ReferenceIdResolver refIdResolver,
                               IntFunction<? extends CoreInstance> internalIdResolver,
                               ElementBuilder elementBuilder,
                               Collection<? super Supplier<? extends FE>> applications,
                               Collection<? super Supplier<? extends AE>> modelElements,
                               Collection<? super Supplier<? extends P>> propertiesFromAssociations,
                               Collection<? super Supplier<? extends QP>> qualifiedPropertiesFromAssociations,
                               Collection<? super Supplier<? extends RU>> refUsages,
                               Collection<? super Supplier<? extends G>> specializations)
    {
        IntFunction<? extends CoreInstance> intIdResolver = (internalIdResolver == null) ? getVacuousInternalIdResolver() : internalIdResolver;
        backReferences.forEach(new BackReferenceConsumer()
        {
            @Override
            protected void accept(BackReference.Application application)
            {
                if (applications != null)
                {
                    applications.add(getApplicationSupplier(application, refIdResolver));
                }
            }

            @Override
            protected void accept(BackReference.ModelElement modelElement)
            {
                if (modelElements != null)
                {
                    modelElements.add(getModelElementSupplier(modelElement, refIdResolver));
                }
            }

            @Override
            protected void accept(BackReference.PropertyFromAssociation propertyFromAssociation)
            {
                if (propertiesFromAssociations != null)
                {
                    propertiesFromAssociations.add(getPropertyFromAssociationSupplier(propertyFromAssociation, refIdResolver));
                }
            }

            @Override
            protected void accept(BackReference.QualifiedPropertyFromAssociation qualifiedPropertyFromAssociation)
            {
                if (qualifiedPropertiesFromAssociations != null)
                {
                    qualifiedPropertiesFromAssociations.add(getQualifiedPropertyFromAssociationSupplier(qualifiedPropertyFromAssociation, refIdResolver));
                }
            }

            @Override
            protected void accept(BackReference.ReferenceUsage referenceUsage)
            {
                if (refUsages != null)
                {
                    refUsages.add(getRefUsageSupplier(referenceUsage, elementBuilder, refIdResolver, intIdResolver));
                }
            }

            @Override
            protected void accept(BackReference.Specialization specialization)
            {
                if (specializations != null)
                {
                    specializations.add(getSpecializationSupplier(specialization, refIdResolver));
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    protected static <FE extends CoreInstance> Supplier<FE> getApplicationSupplier(BackReference.Application application, ReferenceIdResolver idResolver)
    {
        String funcExprId = application.getFunctionExpression();
        return () -> (FE) idResolver.resolveReference(funcExprId);
    }

    @SuppressWarnings("unchecked")
    protected static <AE extends CoreInstance> Supplier<AE> getModelElementSupplier(BackReference.ModelElement modelElement, ReferenceIdResolver idResolver)
    {
        String elementId = modelElement.getElement();
        return () -> (AE) idResolver.resolveReference(elementId);
    }

    @SuppressWarnings("unchecked")
    protected static <P extends CoreInstance> Supplier<P> getPropertyFromAssociationSupplier(BackReference.PropertyFromAssociation propertyFromAssociation, ReferenceIdResolver idResolver)
    {
        String propertyId = propertyFromAssociation.getProperty();
        return () -> (P) idResolver.resolveReference(propertyId);
    }

    @SuppressWarnings("unchecked")
    protected static <QP extends CoreInstance> Supplier<QP> getQualifiedPropertyFromAssociationSupplier(BackReference.QualifiedPropertyFromAssociation propertyFromAssociation, ReferenceIdResolver idResolver)
    {
        String propertyId = propertyFromAssociation.getQualifiedProperty();
        return () -> (QP) idResolver.resolveReference(propertyId);
    }

    @SuppressWarnings("unchecked")
    protected static <RU extends CoreInstance> Supplier<RU> getRefUsageSupplier(BackReference.ReferenceUsage referenceUsage, ElementBuilder elementBuilder, ReferenceIdResolver idResolver, IntFunction<? extends CoreInstance> internalIdResolver)
    {
        PropertyValues offset = PropertyValues.newPropertyValues(M3Properties.offset, M3Paths.ReferenceUsage, Value.newIntegerValue(referenceUsage.getOffset()));
        PropertyValues owner = PropertyValues.newPropertyValues(M3Properties.owner, M3Paths.ReferenceUsage, Reference.newExternalReference(referenceUsage.getOwner()));
        PropertyValues property = PropertyValues.newPropertyValues(M3Properties.propertyName, M3Paths.ReferenceUsage, Value.newStringValue(referenceUsage.getProperty()));

        InstanceData instanceData = InstanceData.newInstanceData(null, M3Paths.ReferenceUsage, referenceUsage.getSourceInformation(), null, CompileStateSet.PROCESSED_VALIDATED.toBitSet(), Lists.immutable.with(offset, owner, property));
        return () -> (RU) elementBuilder.buildComponentInstance(instanceData, Lists.fixedSize.empty(), idResolver, internalIdResolver);
    }

    @SuppressWarnings("unchecked")
    protected static <G extends CoreInstance> Supplier<G> getSpecializationSupplier(BackReference.Specialization specialization, ReferenceIdResolver idResolver)
    {
        String generalizationId = specialization.getGeneralization();
        return () -> (G) idResolver.resolveReference(generalizationId);
    }

    // Values

    protected static <V> OneValue<V> fromValue(V value)
    {
        return OneValue.fromValue(value);
    }

    protected static <V> OneValue<V> fromSupplier(Supplier<? extends V> supplier)
    {
        return OneValue.fromSupplier(supplier);
    }

    protected static <V> ManyValues<V> fromValues(ListIterable<? extends V> propertyValues)
    {
        return ManyValues.fromValues(propertyValues);
    }

    protected static <V> ManyValues<V> fromSuppliers(ListIterable<? extends Supplier<? extends V>> suppliers)
    {
        return ManyValues.fromSuppliers(suppliers);
    }
}

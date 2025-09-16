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
import org.finos.legend.pure.m4.exception.PureCompilationException;
import org.finos.legend.pure.m4.transaction.ModelRepositoryTransaction;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
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

    protected static <V> OneValue<V> newToOnePropertyValue(PropertyValues propertyValues, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver)
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
        return newToOnePropertyValue(values.getFirst(), extResolver, intResolver, primitiveValueResolver);
    }

    @SuppressWarnings("unchecked")
    protected static <V> OneValue<V> newToOnePropertyValue(ValueOrReference propertyValue, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver)
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
                return fromValue((V) primitiveValueResolver.resolveBoolean(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.ByteValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveByte(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.DateValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveDate(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.DateTimeValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveDateTime(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.StrictDateValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveStrictDate(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.LatestDateValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveLatestDate());
            }

            @Override
            public OneValue<V> visit(Value.DecimalValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveDecimal(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.FloatValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveFloat(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.IntegerValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveInteger(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.StrictTimeValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveStrictTime(value.getValue()));
            }

            @Override
            public OneValue<V> visit(Value.StringValue value)
            {
                return fromValue((V) primitiveValueResolver.resolveString(value.getValue()));
            }
        });
    }

    protected static <V> ManyValues<V> newToManyPropertyValue(PropertyValues propertyValues, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver)
    {
        return newToManyPropertyValue(propertyValues, extResolver, intResolver, primitiveValueResolver, null);
    }

    protected static <V> ManyValues<V> newToManyPropertyValue(PropertyValues propertyValues, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver, ListIterable<? extends Supplier<? extends V>> extraSuppliers)
    {
        ListIterable<ValueOrReference> pvList = (propertyValues == null) ? null : propertyValues.getValues();
        if ((extraSuppliers != null) && extraSuppliers.notEmpty())
        {
            if ((pvList == null) || pvList.isEmpty())
            {
                return fromSuppliers(extraSuppliers.toImmutable());
            }
            ValueOrReferenceVisitor<Supplier<V>> visitor = getSupplierVisitor(extResolver, intResolver, primitiveValueResolver);
            MutableList<Supplier<? extends V>> suppliers = Lists.mutable.ofInitialCapacity(pvList.size() + extraSuppliers.size());
            pvList.collect(v -> v.visit(visitor), suppliers);
            suppliers.addAllIterable(extraSuppliers);
            return fromSuppliers(suppliers.toImmutable());
        }
        if ((pvList == null) || pvList.isEmpty())
        {
            return fromValues(null);
        }
        if (pvList.noneSatisfy(v -> v instanceof Reference))
        {
            ValueOrReferenceVisitor<V> visitor = getValueVisitor(primitiveValueResolver);
            return fromValues(pvList.collect(v -> v.visit(visitor), Lists.mutable.ofInitialCapacity(pvList.size())));
        }
        ValueOrReferenceVisitor<Supplier<V>> visitor = getSupplierVisitor(extResolver, intResolver, primitiveValueResolver);
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
    private static <V> ValueOrReferenceVisitor<Supplier<V>> getSupplierVisitor(ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver)
    {
        return new ValueOrReferenceVisitor<Supplier<V>>()
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
        return new SimpleOneValue<>(value);
    }

    protected static <V> OneValue<V> fromSupplier(Supplier<? extends V> supplier)
    {
        return (supplier == null) ? new SimpleOneValue<>(null) : new LazyOneValue<>(supplier);
    }

    protected static <V> ManyValues<V> fromValues(ListIterable<? extends V> propertyValues)
    {
        return new SimpleManyValues<>((propertyValues == null) ? Lists.immutable.empty() : Lists.immutable.withAll(propertyValues));
    }

    protected static <V> ManyValues<V> fromSuppliers(ListIterable<? extends Supplier<? extends V>> suppliers)
    {
        return ((suppliers == null) || suppliers.isEmpty()) ? new SimpleManyValues<>(Lists.immutable.empty()) : new LazyManyValues<>(suppliers);
    }

    protected interface ValueHolder<T>
    {
        boolean isMany();

        int size();

        boolean hasValue();

        T getValue();

        ListIterable<T> getValues();

        <K> CoreInstance getValueByIDIndex(IndexSpecification<K> indexSpec, K key) throws IDConflictException;

        <K> CoreInstance getValueByIDIndex(IndexSpecification<K> indexSpec, K key, Function<? super T, ? extends CoreInstance> toCoreInstance) throws IDConflictException;

        <K> ListIterable<CoreInstance> getValuesByIndex(IndexSpecification<K> indexSpec, K key);

        <K> ListIterable<CoreInstance> getValuesByIndex(IndexSpecification<K> indexSpec, K key, Function<? super T, ? extends CoreInstance> toCoreInstance);

        void setValues(RichIterable<? extends T> values);

        void setValue(int offset, T value);

        void addValue(T value);

        boolean removeValue(Object value);

        void removeAllValues();

        ValueHolder<T> copy();
    }

    protected abstract static class OneValue<T> implements ValueHolder<T>
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
            Supplier<? extends T> local = this.initializer;
            if (local != null)
            {
                synchronized (this)
                {
                    if ((local = this.initializer) != null)
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

    protected abstract static class ManyValues<T> implements ValueHolder<T>
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

    private static class SharedSupplier<T> implements Supplier<T>
    {
        private volatile Supplier<T> supplier;
        private volatile T value;

        private SharedSupplier(Supplier<T> supplier)
        {
            this.supplier = supplier;
        }

        @Override
        public T get()
        {
            if (this.supplier != null)
            {
                synchronized (this)
                {
                    Supplier<T> local = this.supplier;
                    if (local != null)
                    {
                        this.value = local.get();
                        this.supplier = null;
                    }
                }
            }
            return this.value;
        }

        boolean isResolved()
        {
            return this.supplier == null;
        }

        T getResolvedValue()
        {
            return this.value;
        }
    }
}

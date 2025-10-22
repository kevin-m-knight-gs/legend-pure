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
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.serialization.compiler.element.ElementBuilder;
import org.finos.legend.pure.m3.serialization.compiler.element.InstanceData;
import org.finos.legend.pure.m3.serialization.compiler.element.PropertyValues;
import org.finos.legend.pure.m3.serialization.compiler.metadata.BackReference;
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
import org.finos.legend.pure.m4.transaction.ModelRepositoryTransaction;

import java.util.Collection;
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
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, OneValue.fromValue(null));
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, CoreInstance classifier)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, OneValue.fromValue(classifier));
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, Supplier<? extends CoreInstance> classifierSupplier)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, OneValue.fromSupplier(classifierSupplier));
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, String classifierPath, Function<? super String, ? extends CoreInstance> packagePathResolver)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, LazyCoreInstanceUtilities.packageableElementSupplier(packagePathResolver, classifierPath));
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, String classifierPath, ReferenceIdResolvers referenceIdResolvers)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, classifierPath, referenceIdResolvers.packagePathResolver());
    }

    protected AbstractLazyCoreInstance(ModelRepository repository, int internalSyntheticId, String name, SourceInformation sourceInformation, int compileStateBitSet, String classifierPath, ReferenceIdResolver referenceIdResolver)
    {
        this(repository, internalSyntheticId, name, sourceInformation, compileStateBitSet, LazyCoreInstanceUtilities.packageableElementSupplier(referenceIdResolver, classifierPath));
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

    @Override
    public String toString()
    {
        CoreInstance classifier = getClassifier();
        return getName() + "(" + getSyntheticId() + ") instanceOf " + ((classifier == null) ? null : classifier.getName());
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
        return LazyCoreInstanceUtilities.newToOnePropertyValue(propertyValues, extResolver, intResolver, primitiveValueResolver, allowEagerValueResolution);
    }

    protected static <V> ManyValues<V> newToManyPropertyValue(PropertyValues propertyValues, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver, boolean allowEagerValueResolution)
    {
        return LazyCoreInstanceUtilities.newToManyPropertyValue(propertyValues, extResolver, intResolver, primitiveValueResolver, allowEagerValueResolution);
    }

    protected static <V> ManyValues<V> newToManyPropertyValue(PropertyValues propertyValues, ReferenceIdResolver extResolver, IntFunction<? extends CoreInstance> intResolver, PrimitiveValueResolver primitiveValueResolver, boolean allowEagerValueResolution, ListIterable<? extends Supplier<? extends V>> extraSuppliers)
    {
        return LazyCoreInstanceUtilities.newToManyPropertyValue(propertyValues, extResolver, intResolver, primitiveValueResolver, allowEagerValueResolution, extraSuppliers);
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
        return (pkg == null) ? OneValue.fromValue(null) : OneValue.fromSupplier(LazyCoreInstanceUtilities.packageableElementSupplier(packagePathResolver, pkg));
    }

    protected static <T extends CoreInstance> ManyValues<T> computePackageChildren(String path, MetadataIndex metadataIndex, ReferenceIdResolvers referenceIds)
    {
        return computePackageChildren(path, metadataIndex, referenceIds.packagePathResolver());
    }

    protected static <T extends CoreInstance> ManyValues<T> computePackageChildren(String path, MetadataIndex metadataIndex, Function<? super String, ? extends CoreInstance> packagePathResolver)
    {
        ImmutableList<PackageableElementMetadata> children = metadataIndex.getPackageChildren(path);
        return children.isEmpty() ?
               ManyValues.fromValues(null) :
               ManyValues.fromSuppliers(children.collect(child -> LazyCoreInstanceUtilities.packageableElementSupplier(packagePathResolver, child.getPath()), Lists.mutable.ofInitialCapacity(children.size())));
    }

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
        LazyCoreInstanceUtilities.collectBackReferences(backReferences, referenceIds, elementBuilder, applications, modelElements, propertiesFromAssociations, qualifiedPropertiesFromAssociations, refUsages, specializations);
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
        LazyCoreInstanceUtilities.collectBackReferences(backReferences, refIdResolver, internalIdResolver, elementBuilder, applications, modelElements, propertiesFromAssociations, qualifiedPropertiesFromAssociations, refUsages, specializations);
    }
}

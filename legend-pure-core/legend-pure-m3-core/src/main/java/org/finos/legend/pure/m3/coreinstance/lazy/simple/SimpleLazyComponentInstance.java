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

package org.finos.legend.pure.m3.coreinstance.lazy.simple;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.finos.legend.pure.m3.coreinstance.lazy.AbstractLazyCoreInstance;
import org.finos.legend.pure.m3.coreinstance.lazy.PrimitiveValueResolver;
import org.finos.legend.pure.m3.coreinstance.lazy.PropertyValue;
import org.finos.legend.pure.m3.serialization.compiler.element.ElementBuilder;
import org.finos.legend.pure.m3.serialization.compiler.element.InstanceData;
import org.finos.legend.pure.m3.serialization.compiler.metadata.BackReference;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolver;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.CoreInstanceWithStandardPrinting;
import org.finos.legend.pure.m4.coreinstance.compileState.CompileState;
import org.finos.legend.pure.m4.coreinstance.compileState.CompileStateSet;
import org.finos.legend.pure.m4.coreinstance.indexing.IDConflictException;
import org.finos.legend.pure.m4.coreinstance.indexing.IndexSpecification;
import org.finos.legend.pure.m4.transaction.ModelRepositoryTransaction;

import java.util.function.IntFunction;

class SimpleLazyComponentInstance extends AbstractLazyCoreInstance implements CoreInstanceWithStandardPrinting
{
    private volatile SimpleLazyCoreInstanceState state;

    SimpleLazyComponentInstance(ModelRepository repository, InstanceData instanceData, ListIterable<? extends BackReference> backReferences, ReferenceIdResolver referenceIdResolver, IntFunction<? extends CoreInstance> internalIdResolver, PrimitiveValueResolver primitiveValueResolver, ElementBuilder elementBuilder)
    {
        super(repository, repository.nextId(), resolveName(instanceData, repository), instanceData.getSourceInformation(), instanceData.getCompileStateBitSet(), instanceData.getClassifierPath(), referenceIdResolver);
        this.state = new SimpleLazyCoreInstanceState(instanceData.getPropertyValues().size());
        this.state.fromInstanceData(instanceData, backReferences, referenceIdResolver, internalIdResolver, primitiveValueResolver, elementBuilder);
    }

    private SimpleLazyComponentInstance(SimpleLazyComponentInstance source)
    {
        super(source);
        this.state = source.state.copy();
    }

    @Override
    public RichIterable<String> getKeys()
    {
        return getStateForRead().getKeys();
    }

    @Override
    public ListIterable<String> getRealKeyByName(String name)
    {
        return getStateForRead().getRealKey(name);
    }

    @Override
    public void modifyValueForToManyMetaProperty(String key, int offset, CoreInstance value)
    {
        getValueForWrite(key).setValue(offset, value);
    }

    @Override
    public void removeProperty(String propertyNameKey)
    {
        getStateForWrite().removeProperty(propertyNameKey);
    }

    @Override
    public CoreInstance getValueForMetaPropertyToOne(String propertyName)
    {
        PropertyValue<CoreInstance> value = getValueForRead(propertyName);
        return (value == null) ? null : value.getValue();
    }

    @Override
    public ListIterable<? extends CoreInstance> getValueForMetaPropertyToMany(String keyName)
    {
        PropertyValue<CoreInstance> value = getValueForRead(keyName);
        return (value == null) ? Lists.immutable.empty() : value.getValues();
    }

    @Override
    public <K> ListIterable<? extends CoreInstance> getValueInValueForMetaPropertyToManyByIndex(String keyName, IndexSpecification<K> indexSpec, K keyInIndex)
    {
        PropertyValue<CoreInstance> value = getValueForRead(keyName);
        return (value == null) ? Lists.immutable.empty() : value.getValuesByIndex(indexSpec, keyInIndex);
    }

    @Override
    public boolean isValueDefinedForKey(String keyName)
    {
        return getValueForRead(keyName) != null;
    }

    @Override
    public void removeValueForMetaPropertyToMany(String keyName, CoreInstance coreInstance)
    {
        PropertyValue<CoreInstance> value = getValueForWrite(keyName);
        if (value != null)
        {
            value.removeValue(coreInstance);
        }
    }

    @Override
    public void setKeyValues(ListIterable<String> key, ListIterable<? extends CoreInstance> value)
    {
        getStateForWrite().setKeyValues(key, value);
    }

    @Override
    public void addKeyValue(ListIterable<String> key, CoreInstance value)
    {
        getStateForWrite().addKeyValue(key, value);
    }

    @Override
    public void commit(ModelRepositoryTransaction transaction)
    {
        this.state = (SimpleLazyCoreInstanceState) transaction.getState(this);
    }

    @Override
    public boolean hasCompileState(CompileState state)
    {
        return getStateForRead().hasCompileState(state);
    }

    @Override
    public void addCompileState(CompileState state)
    {
        getStateForWrite().addCompileState(state);
    }

    @Override
    public void removeCompileState(CompileState state)
    {
        getStateForWrite().removeCompileState(state);
    }

    @Override
    public CompileStateSet getCompileStates()
    {
        return getStateForRead().getCompileStates();
    }

    @Override
    public void setCompileStatesFrom(CompileStateSet states)
    {
        getStateForWrite().setCompileStatesFrom(states);
    }

    @Override
    public CoreInstance copy()
    {
        return new SimpleLazyComponentInstance(this);
    }

    @Override
    protected <K> CoreInstance getValueByIDIndex(String keyName, IndexSpecification<K> indexSpec, K keyInIndex) throws IDConflictException
    {
        PropertyValue<CoreInstance> value = getValueForRead(keyName);
        return (value == null) ? null : value.getValueByIDIndex(indexSpec, keyInIndex);
    }

    private PropertyValue<CoreInstance> getValueForRead(String propertyName)
    {
        return getStateForRead().getValue(propertyName);
    }

    private PropertyValue<CoreInstance> getValueForWrite(String propertyName)
    {
        return getStateForWrite().getValue(propertyName);
    }

    private SimpleLazyCoreInstanceState getStateForWrite()
    {
        ModelRepositoryTransaction transaction = this.repository.getTransaction();
        if ((transaction != null) && transaction.isOpen())
        {
            if (!transaction.isRegistered(this))
            {
                transaction.registerModified(this, this.state.copy());
            }
            SimpleLazyCoreInstanceState transactionState = (SimpleLazyCoreInstanceState) transaction.getState(this);
            if (transactionState != null)
            {
                return transactionState;
            }
        }
        return this.state;
    }

    private SimpleLazyCoreInstanceState getStateForRead()
    {
        ModelRepositoryTransaction transaction = this.repository.getTransaction();
        if ((transaction != null) && transaction.isOpen())
        {
            SimpleLazyCoreInstanceState transactionState = (SimpleLazyCoreInstanceState) transaction.getState(this);
            if (transactionState != null)
            {
                return transactionState;
            }
        }
        return this.state;
    }

    private static String resolveName(InstanceData instanceData, ModelRepository repository)
    {
        return (instanceData.getName() == null) ? repository.nextAnonymousInstanceName() : instanceData.getName();
    }
}

// Copyright 2020 Goldman Sachs
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

package org.finos.legend.pure.runtime.java.compiled.generation.processors.support.coreinstance;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.runtime.java.compiled.metadata.MetadataLazy;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.RValue;

public abstract class AbstractLazyReflectiveCoreInstance extends ReflectiveCoreInstance
{
    private final MetadataLazy metadataLazy;
    private final ImmutableMap<String, Object> propertyValues;
    private volatile CoreInstance classifier;

    protected AbstractLazyReflectiveCoreInstance(String id, SourceInformation sourceInformation, MetadataLazy metadataLazy, ImmutableMap<String, Object> propertyValues, CoreInstance classifier)
    {
        super(id, sourceInformation);
        this.metadataLazy = metadataLazy;
        this.propertyValues = (propertyValues == null) ? Maps.immutable.empty() : propertyValues;
        this.classifier = classifier;
    }

    protected AbstractLazyReflectiveCoreInstance(String id, SourceInformation sourceInformation, MetadataLazy metadataLazy, ImmutableMap<String, Object> propertyValues)
    {
        this(id, sourceInformation, metadataLazy, propertyValues, null);
    }

    protected AbstractLazyReflectiveCoreInstance(String id, MetadataLazy metadataLazy, ImmutableMap<String, Object> propertyValues)
    {
        this(id, null, metadataLazy, propertyValues);
    }

    protected AbstractLazyReflectiveCoreInstance(String id, SourceInformation sourceInformation, CoreInstance classifier)
    {
        this(id, sourceInformation, null, null, classifier);
    }

    protected AbstractLazyReflectiveCoreInstance(Obj obj, MetadataLazy metadataLazy)
    {
        this(obj.getName(), obj.getSourceInformation(), metadataLazy, metadataLazy.buildMap(obj));
    }

    protected AbstractLazyReflectiveCoreInstance(AbstractLazyReflectiveCoreInstance source)
    {
        this(source.getName(), source.getSourceInformation(), source.metadataLazy, source.propertyValues, source.classifier);
    }

    @Override
    public CoreInstance getClassifier()
    {
        CoreInstance result = this.classifier;
        if (result == null)
        {
            this.classifier = result = this.metadataLazy.getMetadata(M3Paths.Class, getFullSystemPath());
        }
        return result;
    }

    @Override
    public boolean isPersistent()
    {
        return true;
    }

    @SuppressWarnings("unchecked")
    protected <T> T loadValueFromMetadata(String property)
    {
        Object value = this.propertyValues.get(property);
        return (T) this.metadataLazy.valueToObject((RValue) value);
    }

    @SuppressWarnings("unchecked")
    protected <T> RichIterable<T> loadValuesFromMetadata(String property)
    {
        Object values = this.propertyValues.get(property);
        return (RichIterable<T>) this.metadataLazy.valuesToObjects(values);
    }
}

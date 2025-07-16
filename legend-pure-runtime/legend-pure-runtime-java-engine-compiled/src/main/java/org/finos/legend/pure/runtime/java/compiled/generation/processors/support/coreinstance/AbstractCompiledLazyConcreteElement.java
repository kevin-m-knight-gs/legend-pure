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

package org.finos.legend.pure.runtime.java.compiled.generation.processors.support.coreinstance;

import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
import org.finos.legend.pure.m3.coreinstance.lazy.AbstractLazyConcreteElement;
import org.finos.legend.pure.m3.coreinstance.lazy.PrimitiveValueResolver;
import org.finos.legend.pure.m3.execution.ExecutionSupport;
import org.finos.legend.pure.m3.serialization.compiler.element.DeserializedConcreteElement;
import org.finos.legend.pure.m3.serialization.compiler.element.ElementBuilder;
import org.finos.legend.pure.m3.serialization.compiler.element.InstanceData;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ConcreteElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.MetadataIndex;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolver;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolvers;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.tools.SafeAppendable;
import org.finos.legend.pure.runtime.java.compiled.execution.ConsoleCompiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public abstract class AbstractCompiledLazyConcreteElement extends AbstractLazyConcreteElement implements JavaCompiledCoreInstance
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCompiledLazyConcreteElement.class);

    protected AbstractCompiledLazyConcreteElement(ConcreteElementMetadata metadata, MetadataIndex index, ElementBuilder elementBuilder, ReferenceIdResolvers referenceIds, PrimitiveValueResolver primitiveValueResolver, Supplier<? extends DeserializedConcreteElement> deserializer)
    {
        super(null, -1, metadata, index, elementBuilder, referenceIds, primitiveValueResolver, deserializer);
    }

    protected AbstractCompiledLazyConcreteElement(AbstractLazyConcreteElement source)
    {
        super(source);
    }

    @Override
    public void print(Appendable appendable, String tab, int max)
    {
        ConsoleCompiled.append(SafeAppendable.wrap(appendable).append(tab), this, max);
    }

    @Override
    public String toString()
    {
        return toString(null);
    }

    public String toString(ExecutionSupport executionSupport)
    {
        return getName();
    }

    @Override
    public boolean equals(Object obj)
    {
        return pureEquals(obj);
    }

    @Override
    public int hashCode()
    {
        return pureHashCode();
    }

    @Override
    public boolean pureEquals(Object obj)
    {
        return super.equals(obj);
    }

    @Override
    public int pureHashCode()
    {
        return super.hashCode();
    }

    @Override
    protected IntFunction<CoreInstance> newInternalIdResolver(DeserializedConcreteElement deserialized, ElementBuilder elementBuilder, MetadataIndex index, ReferenceIdResolver referenceIdResolver)
    {
        return new InternalIdResolver(this, deserialized, elementBuilder, index, referenceIdResolver);
    }

    protected Function<Object, CoreInstance> getToCoreInstanceFunction()
    {
        return ValCoreInstance::toCoreInstance;
    }

    private static class InternalIdResolver implements IntFunction<CoreInstance>
    {
        private final CoreInstance[] index;

        private InternalIdResolver(CoreInstance concreteElement, DeserializedConcreteElement deserialized, ElementBuilder elementBuilder, MetadataIndex index, ReferenceIdResolver referenceIdResolver)
        {
            ImmutableList<InstanceData> internalInstances = deserialized.getInstanceData();
            LOGGER.debug("Creating {} internal instances for {}", internalInstances.size(), deserialized.getPath());
            this.index = new CoreInstance[internalInstances.size()];
            this.index[0] = concreteElement;
            if (internalInstances.size() > 1)
            {
                internalInstances.forEachWithIndex(1, internalInstances.size() - 1,
                        (d, i) -> this.index[i] = elementBuilder.buildComponentInstance(d, index, referenceIdResolver, this));
            }
        }

        @Override
        public CoreInstance apply(int id)
        {
            try
            {
                // We resolve stubs during loading for compiled mode
                return AnyStubHelper.fromStub(this.index[id]);
            }
            catch (IndexOutOfBoundsException e)
            {
                throw new IllegalArgumentException("Invalid internal id: " + id + " (valid ids are 0-" + (this.index.length - 1) + ")");
            }
        }
    }
}

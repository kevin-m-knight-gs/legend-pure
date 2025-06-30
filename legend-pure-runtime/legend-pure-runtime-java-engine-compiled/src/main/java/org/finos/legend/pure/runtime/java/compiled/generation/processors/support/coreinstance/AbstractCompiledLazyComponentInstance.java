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

import org.finos.legend.pure.m3.coreinstance.lazy.AbstractLazyCoreInstance;
import org.finos.legend.pure.m3.execution.ExecutionSupport;
import org.finos.legend.pure.m3.serialization.compiler.element.InstanceData;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolver;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.tools.SafeAppendable;
import org.finos.legend.pure.runtime.java.compiled.execution.ConsoleCompiled;

import java.util.function.Function;

public abstract class AbstractCompiledLazyComponentInstance extends AbstractLazyCoreInstance implements JavaCompiledCoreInstance
{
    protected AbstractCompiledLazyComponentInstance(InstanceData instanceData, ReferenceIdResolver referenceIdResolver)
    {
        super(null, -1, (instanceData.getName() == null) ? "Anonymous_NoCounter" : instanceData.getName(), instanceData.getSourceInformation(), instanceData.getCompileStateBitSet(), instanceData.getClassifierPath(), referenceIdResolver);
    }

    protected AbstractCompiledLazyComponentInstance(AbstractLazyCoreInstance source)
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
        return ModelRepository.possiblyReplaceAnonymousId(getName());
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

    protected Function<Object, CoreInstance> getToCoreInstanceFunction()
    {
        return ValCoreInstance::toCoreInstance;
    }
}

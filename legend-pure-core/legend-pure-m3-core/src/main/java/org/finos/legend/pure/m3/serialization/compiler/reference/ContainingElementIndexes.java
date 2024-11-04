// Copyright 2024 Goldman Sachs
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

package org.finos.legend.pure.m3.serialization.compiler.reference;

import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3ProcessorSupport;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.tools.PackageTreeIterable;
import org.finos.legend.pure.m4.ModelRepository;

public class ContainingElementIndexes
{
    private ContainingElementIndexes()
    {
        // Static utility class
    }

    public static ContainingElementIndex fromModelRepository(ModelRepository repository)
    {
        return SimpleContainingElementIndex.builder(new M3ProcessorSupport(repository))
                .withElements(repository.getTopLevels())
                .withElements(PackageTreeIterable.newRootPackageTreeIterable(repository).flatCollect(Package::_children))
                .build();
    }

    public static ContainingElementIndex fromProcessorSupport(ProcessorSupport processorSupport)
    {
        SimpleContainingElementIndex.Builder builder = SimpleContainingElementIndex.builder(processorSupport)
                .withElement(processorSupport.repository_getTopLevel(M3Paths.Package))
                .withElement(processorSupport.repository_getTopLevel(M3Paths.Root))
                .withElements(PackageTreeIterable.newRootPackageTreeIterable(processorSupport).flatCollect(Package::_children));
        PrimitiveUtilities.forEachPrimitiveType(processorSupport, builder::addElement);
        return builder.build();
    }
}

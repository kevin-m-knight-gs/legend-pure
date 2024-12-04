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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIds;
import org.finos.legend.pure.m3.tools.PackageTreeIterable;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Arrays;
import java.util.Objects;

public class ModuleMetadataGenerator
{
    private final ConcreteElementMetadataGenerator elementGenerator;
    private final ProcessorSupport processorSupport;

    ModuleMetadataGenerator(ProcessorSupport processorSupport)
    {
        this.processorSupport = Objects.requireNonNull(processorSupport);
        this.elementGenerator = new ConcreteElementMetadataGenerator(ReferenceIds.builder(this.processorSupport).withAvailableExtensions().build().provider(), processorSupport);
    }

    public ModuleMetadata generateModuleMetadata(String name)
    {
        MutableList<ConcreteElementMetadata> elementMetadata = Lists.mutable.empty();
        Lists.mutable.withAll(PrimitiveUtilities.getPrimitiveTypeNames())
                .with(M3Paths.Root)
                .with(M3Paths.Package)
                .asLazy()
                .collect(this.processorSupport::repository_getTopLevel)
                .collectIf(e -> isSourceInModule(name, e.getSourceInformation()),
                        this.elementGenerator::generateMetadata,
                        elementMetadata);
        PackageTreeIterable.newRootPackageTreeIterable(this.processorSupport)
                .flatCollect(Package::_children)
                .collectIf(e -> isSourceInModule(name, e.getSourceInformation()),
                        this.elementGenerator::generateMetadata,
                        elementMetadata);
        return new ModuleMetadata(name, elementMetadata);
    }

    public ModuleMetadata updateElements(ModuleMetadata metadata, String... elementsToUpdate)
    {
        return updateElements(metadata, Arrays.asList(elementsToUpdate));
    }

    public ModuleMetadata updateElements(ModuleMetadata metadata, Iterable<? extends String> elementsToUpdate)
    {
        MutableList<ConcreteElementMetadata> newElements = Lists.mutable.empty();
        MutableSet<String> toRemove = Sets.mutable.empty();
        elementsToUpdate.forEach(path ->
        {
            CoreInstance element = this.processorSupport.package_getByUserPath(path);
            if (element == null)
            {
                toRemove.add(path);
            }
            else if (isSourceInModule(metadata.getName(), element.getSourceInformation()))
            {
                newElements.add(this.elementGenerator.generateMetadata(element));
            }
        });
        return metadata.update(newElements, toRemove);
    }

    public ModuleMetadata updateAllElements(ModuleMetadata metadata, String... moreElementsToUpdate)
    {
        return updateAllElements(metadata, Arrays.asList(moreElementsToUpdate));
    }

    public ModuleMetadata updateAllElements(ModuleMetadata metadata, Iterable<? extends String> moreElementsToUpdate)
    {
        MutableSet<String> extraUpdates = Sets.mutable.withAll(moreElementsToUpdate);
        MutableList<ConcreteElementMetadata> newElements = Lists.mutable.empty();
        MutableSet<String> toRemove = Sets.mutable.empty();
        metadata.forEachElement(elementMetadata ->
        {
            String path = elementMetadata.getPath();
            extraUpdates.remove(path);
            CoreInstance element = this.processorSupport.package_getByUserPath(path);
            if (element == null)
            {
                toRemove.add(elementMetadata.getPath());
            }
            else
            {
                ConcreteElementMetadata newElementMetadata = this.elementGenerator.generateMetadata(element);
                if (!newElementMetadata.equals(elementMetadata))
                {
                    newElements.add(newElementMetadata);
                }
            }
        });
        extraUpdates.forEach(path ->
        {
            CoreInstance element = this.processorSupport.package_getByUserPath(path);
            if (element != null)
            {
                newElements.add(this.elementGenerator.generateMetadata(element));
            }
        });
        return (newElements.isEmpty() && toRemove.isEmpty()) ? metadata : metadata.update(newElements, toRemove);
    }

    ConcreteElementMetadataGenerator getElementMetadataGenerator()
    {
        return this.elementGenerator;
    }

    static boolean isSourceInModule(String moduleName, SourceInformation sourceInfo)
    {
        return (sourceInfo != null) && ModuleMetadata.isSourceInModule(moduleName, sourceInfo.getSourceId());
    }
}

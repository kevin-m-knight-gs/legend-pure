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
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIds;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.CodeStorageTools;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Arrays;
import java.util.Objects;

public class ModuleMetadataGenerator
{
    private final ConcreteElementMetadataGenerator elementGenerator;
    private final ProcessorSupport processorSupport;

    public ModuleMetadataGenerator(ProcessorSupport processorSupport)
    {
        this.processorSupport = Objects.requireNonNull(processorSupport);
        this.elementGenerator = new ConcreteElementMetadataGenerator(ReferenceIds.builder(this.processorSupport).withAvailableExtensions().build().provider(), processorSupport);
    }

    public ModuleMetadata generateModuleMetadata(String name)
    {
        return new ModuleMetadata(name,
                GraphTools.getTopLevelAndPackagedElements(this.processorSupport)
                        .collectIf(
                                e -> isSourceInModule(name, e.getSourceInformation()),
                                this.elementGenerator::generateMetadata));
    }

    public MutableList<ModuleMetadata> generateModuleMetadata(Iterable<String> moduleNames)
    {
        MutableMap<String, MutableList<ConcreteElementMetadata>> metadataByModule = Maps.mutable.empty();
        moduleNames.forEach(name -> metadataByModule.put(Objects.requireNonNull(name), Lists.mutable.empty()));
        if (metadataByModule.isEmpty())
        {
            return Lists.mutable.empty();
        }
        if (metadataByModule.size() == 1)
        {
            return Lists.mutable.with(generateModuleMetadata(metadataByModule.keysView().getAny()));
        }
        GraphTools.getTopLevelAndPackagedElements(this.processorSupport)
                .forEach(element ->
                {
                    String moduleName = getModule(element.getSourceInformation());
                    if (moduleName != null)
                    {
                        MutableList<ConcreteElementMetadata> elementMetadata = metadataByModule.get(moduleName);
                        if (elementMetadata != null)
                        {
                            elementMetadata.add(this.elementGenerator.generateMetadata(element));
                        }
                    }
                });
        MutableList<ModuleMetadata> results = Lists.mutable.ofInitialCapacity(metadataByModule.size());
        metadataByModule.forEachKeyValue((name, elementMetadata) -> results.add(new ModuleMetadata(name, elementMetadata)));
        return results;
    }

    public MutableList<ModuleMetadata> generateModuleMetadata(String... moduleNames)
    {
        switch (moduleNames.length)
        {
            case 0:
            {
                return Lists.mutable.empty();
            }
            case 1:
            {
                return Lists.mutable.with(generateModuleMetadata(moduleNames[0]));
            }
            default:
            {
                return generateModuleMetadata(Arrays.asList(moduleNames));
            }
        }
    }

    public MutableList<ModuleMetadata> generateAllModuleMetadata()
    {
        MutableMap<String, MutableList<ConcreteElementMetadata>> metadataByModule = Maps.mutable.empty();
        GraphTools.getTopLevelAndPackagedElements(this.processorSupport)
                .forEach(element ->
                {
                    String moduleName = getModule(element.getSourceInformation());
                    if (moduleName != null)
                    {
                        metadataByModule.getIfAbsentPut(moduleName, Lists.mutable::empty).add(this.elementGenerator.generateMetadata(element));
                    }
                });
        MutableList<ModuleMetadata> results = Lists.mutable.ofInitialCapacity(metadataByModule.size());
        metadataByModule.forEachKeyValue((name, elementMetadata) -> results.add(new ModuleMetadata(name, elementMetadata)));
        return results;
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

    private static boolean isSourceInModule(String moduleName, SourceInformation sourceInfo)
    {
        return (sourceInfo != null) && ModuleMetadata.isSourceInModule(moduleName, sourceInfo.getSourceId());
    }

    private static String getModule(SourceInformation sourceInfo)
    {
        return (sourceInfo == null) ? null : CodeStorageTools.getInitialPathElement(sourceInfo.getSourceId());
    }
}

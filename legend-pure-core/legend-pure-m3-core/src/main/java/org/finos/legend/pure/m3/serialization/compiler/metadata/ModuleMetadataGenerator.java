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

import org.eclipse.collections.api.LazyIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProvider;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIds;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.CodeStorageTools;
import org.finos.legend.pure.m3.serialization.runtime.PureRuntime;
import org.finos.legend.pure.m3.serialization.runtime.Source;
import org.finos.legend.pure.m3.serialization.runtime.SourceRegistry;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Arrays;
import java.util.Objects;

public class ModuleMetadataGenerator
{
    private final ProcessorSupport processorSupport;
    private final SourceRegistry sourceRegistry;
    private final ConcreteElementMetadataGenerator elementGenerator;
    private final SourceMetadataGenerator sourceGenerator;

    public ModuleMetadataGenerator(ProcessorSupport processorSupport, SourceRegistry sourceRegistry, ReferenceIdProvider referenceIdProvider)
    {
        this.processorSupport = Objects.requireNonNull(processorSupport);
        this.sourceRegistry = sourceRegistry;
        this.elementGenerator = new ConcreteElementMetadataGenerator((referenceIdProvider == null) ? ReferenceIds.builder(processorSupport).withAvailableExtensions().build().provider() : referenceIdProvider, processorSupport);
        this.sourceGenerator = (sourceRegistry == null) ? null : new SourceMetadataGenerator();
    }

    public ModuleMetadataGenerator(ProcessorSupport processorSupport, SourceRegistry sourceRegistry)
    {
        this(processorSupport, sourceRegistry, null);
    }

    public ModuleMetadataGenerator(ProcessorSupport processorSupport)
    {
        this(processorSupport, null);
    }

    public ModuleMetadataGenerator(PureRuntime runtime)
    {
        this(runtime.getProcessorSupport(), runtime.getSourceRegistry());
    }

    public ModuleMetadata generateModuleMetadata(String name)
    {
        ModuleMetadata.Builder builder = ModuleMetadata.builder(name).withElements(getModuleElementMetadata(name));
        if (this.sourceRegistry != null)
        {
            builder.addSources(getModuleSourceMetadata(name));
        }
        return builder.build();
    }

    public MutableList<ModuleMetadata> generateModuleMetadata(Iterable<String> moduleNames)
    {
        MutableMap<String, ModuleMetadata.Builder> buildersByModule = Maps.mutable.empty();
        moduleNames.forEach(name -> buildersByModule.put(Objects.requireNonNull(name), ModuleMetadata.builder(name)));
        if (buildersByModule.isEmpty())
        {
            return Lists.mutable.empty();
        }
        if (buildersByModule.size() == 1)
        {
            String moduleName = buildersByModule.keysView().getAny();
            ModuleMetadata.Builder builder = buildersByModule.valuesView().getAny().withElements(getModuleElementMetadata(moduleName));
            if (this.sourceRegistry != null)
            {
                builder.addSources(getModuleSourceMetadata(moduleName));
            }
            return Lists.mutable.with(builder.build());
        }
        GraphTools.getTopLevelAndPackagedElements(this.processorSupport).forEach(element ->
        {
            String moduleName = getModule(element.getSourceInformation());
            if (moduleName != null)
            {
                ModuleMetadata.Builder builder = buildersByModule.get(moduleName);
                if (builder != null)
                {
                    builder.addElement(this.elementGenerator.generateMetadata(element));
                }
            }
        });
        if (this.sourceRegistry != null)
        {
            this.sourceRegistry.getSources().forEach(source ->
            {
                String moduleName = getModule(source.getId());
                if (moduleName != null)
                {
                    ModuleMetadata.Builder builder = buildersByModule.get(moduleName);
                    if (builder != null)
                    {
                        builder.addSource(this.sourceGenerator.generateSourceMetadata(source));
                    }
                }
            });
        }

        return buildersByModule.collect(ModuleMetadata.Builder::build, Lists.mutable.ofInitialCapacity(buildersByModule.size()));
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
        MutableMap<String, ModuleMetadata.Builder> buildersByModule = Maps.mutable.empty();
        GraphTools.getTopLevelAndPackagedElements(this.processorSupport).forEach(element ->
        {
            String moduleName = getModule(element.getSourceInformation());
            if (moduleName != null)
            {
                buildersByModule.getIfAbsentPutWithKey(moduleName, ModuleMetadata::builder).addElement(this.elementGenerator.generateMetadata(element));
            }
        });
        if (this.sourceRegistry != null)
        {
            this.sourceRegistry.getSources().forEach(source ->
            {
                String moduleName = getModule(source.getId());
                if (moduleName != null)
                {
                    buildersByModule.getIfAbsentPutWithKey(moduleName, ModuleMetadata::builder).addSource(this.sourceGenerator.generateSourceMetadata(source));
                }
            });
        }
        return buildersByModule.collect(ModuleMetadata.Builder::build, Lists.mutable.ofInitialCapacity(buildersByModule.size()));
    }

    public ModuleMetadata update(ModuleMetadata metadata, Iterable<? extends String> elementsToUpdate, Iterable<? extends String> sourcesToUpdate)
    {
        MutableList<ConcreteElementMetadata> newElements = Lists.mutable.empty();
        MutableSet<String> elementsToRemove = Sets.mutable.empty();
        elementsToUpdate.forEach(path ->
        {
            CoreInstance element = this.processorSupport.package_getByUserPath(path);
            if (element == null)
            {
                elementsToRemove.add(path);
            }
            else if (isSourceInModule(metadata.getName(), element.getSourceInformation()))
            {
                newElements.add(this.elementGenerator.generateMetadata(element));
            }
        });
        if (this.sourceRegistry == null)
        {
            return metadata.update(newElements, elementsToRemove, null, null);
        }

        MutableList<SourceMetadata> newSources = Lists.mutable.empty();
        MutableSet<String> sourcesToRemove = Sets.mutable.empty();
        sourcesToUpdate.forEach(sourceId ->
        {
            Source source = this.sourceRegistry.getSource(sourceId);
            if (source == null)
            {
                sourcesToRemove.add(sourceId);
            }
            else if (ModuleMetadata.isSourceInModule(metadata.getName(), sourceId))
            {
                newSources.add(this.sourceGenerator.generateSourceMetadata(source));
            }
        });
        return metadata.update(newElements, elementsToRemove, newSources, sourcesToRemove);
    }

    public ModuleMetadata updateAll(ModuleMetadata metadata)
    {
        return updateAll(metadata, Lists.immutable.empty(), Lists.immutable.empty());
    }

    public ModuleMetadata updateAll(ModuleMetadata metadata, Iterable<? extends String> moreElementsToUpdate, Iterable<? extends String> moreSourcesToUpdate)
    {
        MutableSet<String> extraElementUpdates = Sets.mutable.withAll(moreElementsToUpdate);
        MutableSet<String> extraSourceUpdates = (this.sourceRegistry == null) ? null : Sets.mutable.withAll(moreSourcesToUpdate);

        MutableList<ConcreteElementMetadata> newElements = Lists.mutable.empty();
        MutableSet<String> elementsToRemove = Sets.mutable.empty();
        metadata.forEachElement(elementMetadata ->
        {
            String path = elementMetadata.getPath();
            extraElementUpdates.remove(path);
            CoreInstance element = this.processorSupport.package_getByUserPath(path);
            if (element == null)
            {
                elementsToRemove.add(elementMetadata.getPath());
                if (extraSourceUpdates != null)
                {
                    extraSourceUpdates.add(elementMetadata.getSourceInformation().getSourceId());
                }
            }
            else
            {
                ConcreteElementMetadata newElementMetadata = this.elementGenerator.generateMetadata(element);
                if (!newElementMetadata.equals(elementMetadata))
                {
                    newElements.add(newElementMetadata);
                    if (extraSourceUpdates != null)
                    {
                        extraSourceUpdates.add(elementMetadata.getSourceInformation().getSourceId());
                        extraSourceUpdates.add(newElementMetadata.getSourceInformation().getSourceId());
                    }
                }
            }
        });
        extraElementUpdates.forEach(path ->
        {
            CoreInstance element = this.processorSupport.package_getByUserPath(path);
            if (element != null)
            {
                newElements.add(this.elementGenerator.generateMetadata(element));
                if (extraSourceUpdates != null)
                {
                    extraSourceUpdates.add(element.getSourceInformation().getSourceId());
                }
            }
        });
        if (this.sourceRegistry == null)
        {
            return (newElements.isEmpty() && elementsToRemove.isEmpty()) ? metadata : metadata.update(newElements, elementsToRemove, null, null);
        }

        MutableList<SourceMetadata> newSources = Lists.mutable.empty();
        MutableSet<String> sourcesToRemove = Sets.mutable.empty();
        metadata.forEachSource(sourceMetadata ->
        {
            String sourceId = sourceMetadata.getSourceId();
            extraSourceUpdates.remove(sourceId);
            Source source = this.sourceRegistry.getSource(sourceId);
            if (source == null)
            {
                sourcesToRemove.add(sourceId);
            }
            else
            {
                SourceMetadata newSourceMetadata = this.sourceGenerator.generateSourceMetadata(source);
                if (!newSourceMetadata.equals(sourceMetadata))
                {
                    newSources.add(newSourceMetadata);
                }
            }
        });
        extraSourceUpdates.forEach(sourceId ->
        {
            Source source = this.sourceRegistry.getSource(sourceId);
            if (source != null)
            {
                newSources.add(this.sourceGenerator.generateSourceMetadata(source));
            }
        });

        return (newElements.isEmpty() && elementsToRemove.isEmpty() && newSources.isEmpty() && sourcesToRemove.isEmpty()) ?
               metadata :
               metadata.update(newElements, elementsToRemove, newSources, sourcesToRemove);
    }

    private LazyIterable<ConcreteElementMetadata> getModuleElementMetadata(String moduleName)
    {
        return GraphTools.getTopLevelAndPackagedElements(this.processorSupport).collectIf(
                e -> isSourceInModule(moduleName, e.getSourceInformation()),
                this.elementGenerator::generateMetadata);
    }

    private LazyIterable<SourceMetadata> getModuleSourceMetadata(String moduleName)
    {
        return this.sourceRegistry.getSources().asLazy().collectIf(
                s -> ModuleMetadata.isSourceInModule(moduleName, s.getId()),
                this.sourceGenerator::generateSourceMetadata);
    }

    ConcreteElementMetadataGenerator getElementMetadataGenerator()
    {
        return this.elementGenerator;
    }

    SourceMetadataGenerator getSourceMetadataGenerator()
    {
        return this.sourceGenerator;
    }

    private static boolean isSourceInModule(String moduleName, SourceInformation sourceInfo)
    {
        return (sourceInfo != null) && ModuleMetadata.isSourceInModule(moduleName, sourceInfo.getSourceId());
    }

    private static String getModule(SourceInformation sourceInfo)
    {
        return (sourceInfo == null) ? null : getModule(sourceInfo.getSourceId());
    }

    private static String getModule(String sourceId)
    {
        return CodeStorageTools.getInitialPathElement(sourceId);
    }
}

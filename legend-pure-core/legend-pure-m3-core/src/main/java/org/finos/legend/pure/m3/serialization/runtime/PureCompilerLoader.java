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

package org.finos.legend.pure.m3.serialization.runtime;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.pure.m3.compiler.Context;
import org.finos.legend.pure.m3.coreinstance.lazy.generator.M3GeneratedLazyElementBuilder;
import org.finos.legend.pure.m3.serialization.compiler.element.ConcreteElementDeserializer;
import org.finos.legend.pure.m3.serialization.compiler.element.ElementLoader;
import org.finos.legend.pure.m3.serialization.compiler.file.FileDeserializer;
import org.finos.legend.pure.m3.serialization.compiler.file.FilePathProvider;
import org.finos.legend.pure.m3.serialization.compiler.metadata.MetadataIndex;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleFunctionNameMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleManifest;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataSerializer;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleSourceMetadata;
import org.finos.legend.pure.m3.serialization.compiler.strings.StringIndexer;
import org.finos.legend.pure.m3.serialization.grammar.Parser;
import org.finos.legend.pure.m3.serialization.grammar.ParserLibrary;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public abstract class PureCompilerLoader
{
    final ClassLoader classLoader;
    final FileDeserializer fileDeserializer;

    private PureCompilerLoader(ClassLoader classLoader)
    {
        this.classLoader = Objects.requireNonNull(classLoader);
        StringIndexer stringIndexer = StringIndexer.builder()
                .withLoadedExtensions(classLoader)
                .build();
        ConcreteElementDeserializer elementDeserializer = ConcreteElementDeserializer.builder()
                .withLoadedExtensions(classLoader)
                .withStringIndexer(stringIndexer)
                .build();
        ModuleMetadataSerializer moduleMetadataSerializer = ModuleMetadataSerializer.builder()
                .withLoadedExtensions(classLoader)
                .withStringIndexer(stringIndexer)
                .build();
        this.fileDeserializer = FileDeserializer.builder()
                .withFilePathProvider(FilePathProvider.builder().withLoadedExtensions(classLoader).build())
                .withSerializers(elementDeserializer, moduleMetadataSerializer)
                .build();
    }

    public boolean canLoad(String repository)
    {
        return moduleManifestExists(repository);
    }

    public void load(PureRuntime runtime, String... repositories)
    {
        load(runtime, Sets.mutable.with(repositories));
    }

    public void load(PureRuntime runtime, Iterable<? extends String> repositories)
    {
        Set<? extends String> repoSet = (repositories instanceof Set) ? (Set<? extends String>) repositories : Sets.mutable.withAll(repositories);
        if (!repoSet.isEmpty())
        {
            load(runtime, Lists.mutable.withAll(repoSet));
        }
    }

    private void load(PureRuntime runtime, MutableList<? extends String> repositories)
    {
        MetadataIndex metadataIndex = MetadataIndex.builder()
                .withModules(LazyIterate.collect(repositories, this::loadModuleManifest))
                .build();

        ModelRepository repository = runtime.getModelRepository();
        ElementLoader elementLoader = ElementLoader.builder()
                .withMetadataIndex(metadataIndex)
                .withClassLoader(this.classLoader)
                .withAvailableReferenceIdExtensions(this.classLoader)
                .withFileDeserializer(this.fileDeserializer)
                .withElementBuilder(M3GeneratedLazyElementBuilder.newElementBuilder(this.classLoader, repository))
                .build();

        // initialize top level elements
        GraphTools.getTopLevelNames().forEach(n -> repository.addTopLevel(elementLoader.loadElementStrict(n)));

        // initialize source registry
        ParserLibrary parserLibrary = runtime.getIncrementalCompiler().getParserLibrary();
        repositories.forEach(repo -> loadModuleSourceMetadata(repo).forEachSource(sourceMetadata ->
        {
            String sourceId = sourceMetadata.getSourceId();
            runtime.loadSourceIfLoadable(sourceId);
            Source source = runtime.getSourceById(sourceId);
            if (source == null)
            {
                throw new RuntimeException("Unknown source: " + sourceId);
            }

            MutableListMultimap<Parser, CoreInstance> instancesByParser = Multimaps.mutable.list.empty();
            sourceMetadata.getSections().forEach(section ->
            {
                Parser parser = parserLibrary.getParser(section.getParser()).newInstance(parserLibrary);
                MutableList<CoreInstance> elements = section.getElements().collect(elementLoader::loadElementStrict, Lists.mutable.ofInitialCapacity(section.getElements().size()));
                instancesByParser.putAll(parser, elements);
            });

            source.linkInstances(instancesByParser);
        }));

        // load functions by name
        Context context = runtime.getContext();
        repositories.forEach(repo -> loadModuleFunctionsByName(repo).getFunctionsByName().forEach(fbn ->
        {
            String funcName = fbn.getFunctionName();
            ImmutableList<String> funcPaths = fbn.getFunctions();
            context.registerFunctionsByName(funcName, funcPaths.collect(elementLoader::loadElementStrict, Lists.mutable.ofInitialCapacity(funcPaths.size())));
        }));
    }

    abstract boolean moduleManifestExists(String repository);

    abstract ModuleManifest loadModuleManifest(String repository);

    abstract ModuleSourceMetadata loadModuleSourceMetadata(String repository);

    abstract ModuleFunctionNameMetadata loadModuleFunctionsByName(String repository);

    public static PureCompilerLoader newLoader(ClassLoader classLoader)
    {
        return new ClassLoaderPureCompilerLoader(classLoader);
    }

    public static PureCompilerLoader newLoader(ClassLoader classLoader, Path directory)
    {
        return new DirectoryPureCompilerLoader(classLoader, directory);
    }

    private static class ClassLoaderPureCompilerLoader extends PureCompilerLoader
    {
        private ClassLoaderPureCompilerLoader(ClassLoader classLoader)
        {
            super(classLoader);
        }

        @Override
        boolean moduleManifestExists(String repository)
        {
            return this.fileDeserializer.moduleManifestExists(this.classLoader, repository);
        }

        @Override
        ModuleManifest loadModuleManifest(String repository)
        {
            return this.fileDeserializer.deserializeModuleManifest(this.classLoader, repository);
        }

        @Override
        ModuleSourceMetadata loadModuleSourceMetadata(String repository)
        {
            return this.fileDeserializer.deserializeModuleSourceMetadata(this.classLoader, repository);
        }

        @Override
        ModuleFunctionNameMetadata loadModuleFunctionsByName(String repository)
        {
            return this.fileDeserializer.deserializeModuleFunctionNameMetadata(this.classLoader, repository);
        }
    }

    private static class DirectoryPureCompilerLoader extends PureCompilerLoader
    {
        private final Path directory;

        private DirectoryPureCompilerLoader(ClassLoader classLoader, Path directory)
        {
            super(classLoader);
            this.directory = Objects.requireNonNull(directory);
        }

        @Override
        boolean moduleManifestExists(String repository)
        {
            return this.fileDeserializer.moduleManifestExists(this.directory, repository);
        }

        @Override
        ModuleManifest loadModuleManifest(String repository)
        {
            return this.fileDeserializer.deserializeModuleManifest(this.directory, repository);
        }

        @Override
        ModuleSourceMetadata loadModuleSourceMetadata(String repository)
        {
            return this.fileDeserializer.deserializeModuleSourceMetadata(this.directory, repository);
        }

        @Override
        ModuleFunctionNameMetadata loadModuleFunctionsByName(String repository)
        {
            return this.fileDeserializer.deserializeModuleFunctionNameMetadata(this.directory, repository);
        }
    }
}

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

package org.finos.legend.pure.m3.serialization.runtime;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.block.function.Function;
import org.eclipse.collections.api.block.procedure.Procedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.multimap.list.MutableListMultimap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.impl.factory.Multimaps;
import org.eclipse.collections.impl.utility.Iterate;
import org.eclipse.collections.impl.utility.LazyIterate;
import org.finos.legend.pure.m3.compiler.Context;
import org.finos.legend.pure.m3.compiler.postprocessing.GenericTypeTraceability;
import org.finos.legend.pure.m3.compiler.postprocessing.SpecializationProcessor;
import org.finos.legend.pure.m3.compiler.postprocessing.processor.AnnotatedElementProcessor;
import org.finos.legend.pure.m3.compiler.postprocessing.processor.Processor;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.FunctionDefinition;
import org.finos.legend.pure.m3.navigation.Instance;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3ProcessorSupport;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation.property.Property;
import org.finos.legend.pure.m3.navigation.type.Type;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.CodeStorageTools;
import org.finos.legend.pure.m3.serialization.grammar.Parser;
import org.finos.legend.pure.m3.serialization.grammar.ParserLibrary;
import org.finos.legend.pure.m3.serialization.grammar.m3parser.inlinedsl.InlineDSL;
import org.finos.legend.pure.m3.serialization.grammar.m3parser.inlinedsl.InlineDSLLibrary;
import org.finos.legend.pure.m3.serialization.runtime.binary.BinaryModelSourceDeserializer;
import org.finos.legend.pure.m3.serialization.runtime.binary.DeserializationNode;
import org.finos.legend.pure.m3.serialization.runtime.binary.DeserializationNode.ReferenceResolutionResult;
import org.finos.legend.pure.m3.serialization.runtime.binary.PureRepositoryJar;
import org.finos.legend.pure.m3.serialization.runtime.binary.PureRepositoryJarLibrary;
import org.finos.legend.pure.m3.serialization.runtime.binary.PureRepositoryJarTools;
import org.finos.legend.pure.m3.serialization.runtime.binary.PureRepositoryJars;
import org.finos.legend.pure.m3.serialization.runtime.binary.SourceDeserializationResult;
import org.finos.legend.pure.m3.serialization.runtime.binary.reference.CachedReferenceFactory;
import org.finos.legend.pure.m3.serialization.runtime.binary.reference.ExternalReferenceSerializerLibrary;
import org.finos.legend.pure.m3.serialization.runtime.binary.reference.Reference;
import org.finos.legend.pure.m3.serialization.runtime.binary.reference.ReferenceFactory;
import org.finos.legend.pure.m3.serialization.runtime.binary.reference.SimpleReferenceFactory;
import org.finos.legend.pure.m3.serialization.runtime.pattern.URLPatternLibrary;
import org.finos.legend.pure.m3.tools.forkjoin.ForkJoinTools;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.binary.BinaryReaders;

import java.net.URL;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class GraphLoader
{
    private static final int DESERIALIZE_FILES_THRESHOLD = 1000;
    private static final int INITIALIZE_NODES_THRESHOLD = 10_000;
    private static final int RESOLVE_REFERENCES_THRESHOLD = 1000;
    private static final int POPULATE_BACK_REFERENCES_THRESHOLD = 1000;
    private static final int UPDATE_CONTEXT_THRESHOLD = 10_000;

    private final ModelRepository repository;
    private final Context context;
    private final ProcessorSupport processorSupport;
    private final ParserLibrary parserLibrary;
    private final InlineDSLLibrary inlineDSLLibrary;
    private final SourceRegistry sourceRegistry;
    private final URLPatternLibrary patternLibrary;
    private final PureRepositoryJarLibrary jarLibrary;
    private final MutableSet<String> loadedFiles = Sets.mutable.empty();
    private final ForkJoinPool forkJoinPool;

    public GraphLoader(ModelRepository repository, Context context, ParserLibrary parserLibrary, InlineDSLLibrary inlineDSLLibrary, SourceRegistry sourceRegistry, URLPatternLibrary patternLibrary, PureRepositoryJarLibrary jarLibrary, ForkJoinPool forkJoinPool)
    {
        this.repository = repository;
        this.context = context;
        this.parserLibrary = parserLibrary;
        this.inlineDSLLibrary = inlineDSLLibrary;
        this.sourceRegistry = sourceRegistry;
        this.patternLibrary = patternLibrary;
        this.jarLibrary = jarLibrary;
        this.forkJoinPool = forkJoinPool;
        this.processorSupport = new M3ProcessorSupport(context, repository);
    }

    public GraphLoader(ModelRepository repository, Context context, ParserLibrary parserLibrary, InlineDSLLibrary inlineDSLLibrary, SourceRegistry sourceRegistry, URLPatternLibrary patternLibrary, PureRepositoryJarLibrary jarLibrary)
    {
        this(repository, context, parserLibrary, inlineDSLLibrary, sourceRegistry, patternLibrary, jarLibrary, null);
    }

    public void loadAll()
    {
        loadAll(null);
    }

    public void loadAll(Message message)
    {
        if (message != null)
        {
            message.setMessage("    Reading all files ...");
        }
        MapIterable<String, byte[]> fileBytes = this.loadedFiles.isEmpty() ? this.jarLibrary.readAllFiles() : this.jarLibrary.readFiles(this.jarLibrary.getAllFiles().reject(this::fileIsLoaded));
        if (message != null)
        {
            message.setMessage(String.format("    Reading all (%,d) files ...", fileBytes.size()));
        }
        loadFileBytes(fileBytes, message);
    }

    public boolean isKnownRepository(String repositoryName)
    {
        return this.jarLibrary.isKnownRepository(repositoryName);
    }

    public void loadRepository(String repositoryName)
    {
        loadRepository(repositoryName, null);
    }

    public void loadRepository(String repositoryName, Message message)
    {
        if (message != null)
        {
            message.setMessage("  Loading '" + repositoryName + "' from PAR with " + this.jarLibrary.getFileDependencies(this.jarLibrary.getRepositoryFiles(repositoryName)).size() + " dependencies");
        }
        loadFiles_internal(this.jarLibrary.getFileDependencies(this.jarLibrary.getRepositoryFiles(repositoryName)), message);
    }

    public void loadRepositories(Iterable<String> repositoryNames)
    {
        loadRepositories(repositoryNames, null);
    }

    public void loadRepositories(Iterable<String> repositoryNames, Message message)
    {
        MutableList<String> files = Iterate.flatCollect(repositoryNames, this.jarLibrary::getRepositoryFiles, Lists.mutable.empty());
        SetIterable<String> allFiles = this.jarLibrary.getFileDependencies(files);
        loadFiles_internal(allFiles, message);
    }

    public boolean isKnownFile(String path)
    {
        return this.jarLibrary.isKnownFile(path);
    }

    public void loadFile(String path)
    {
        loadFile(path, null);
    }

    public void loadFile(String path, Message message)
    {
        loadFiles_internal(this.jarLibrary.getFileDependencies(convertToBinaryPath(path)), message);
    }

    public void loadFiles(Iterable<String> paths)
    {
        loadFiles(paths, null);
    }

    public void loadFiles(Iterable<String> paths, Message message)
    {
        loadFiles_internal(this.jarLibrary.getFileDependencies(LazyIterate.collect(paths, GraphLoader::convertToBinaryPath)), message);
    }

    public void loadDirectoryFiles(String directory)
    {
        loadDirectoryFiles(directory, null);
    }

    public void loadDirectoryFiles(String directory, Message message)
    {
        loadFiles_internal(this.jarLibrary.getFileDependencies(this.jarLibrary.getDirectoryFiles(directory)), message);
    }

    public boolean isKnownInstance(String instancePath)
    {
        return this.jarLibrary.isKnownInstance(instancePath);
    }

    public void loadInstance(String instancePath)
    {
        loadInstance(instancePath, null);
    }

    public void loadInstance(String instancePath, Message message)
    {
        loadFiles_internal(this.jarLibrary.getRequiredFiles(instancePath), message);
    }

    public void loadInstances(Iterable<String> instancePaths)
    {
        loadInstances(instancePaths, null);
    }

    public void loadInstances(Iterable<String> instancePaths, Message message)
    {
        loadFiles_internal(this.jarLibrary.getRequiredFiles(instancePaths), message);
    }

    public SetIterable<String> getLoadedFiles()
    {
        return this.loadedFiles.asUnmodifiable();
    }

    private boolean fileIsLoaded(String file)
    {
        return this.loadedFiles.contains(file);
    }

    private void loadFiles_internal(SetIterable<String> files, Message message)
    {
        if (message != null)
        {
            message.setMessage(String.format("    Reading %,d files ...", +files.size()));
        }
        MapIterable<String, byte[]> fileBytes = this.jarLibrary.readFiles(LazyIterate.reject(files, this::fileIsLoaded));
        loadFileBytes(fileBytes, message);
    }

    private void loadFileBytes(MapIterable<String, byte[]> fileBytes, Message message)
    {
        if (fileBytes.notEmpty())
        {
            ListIterable<SourceDeserializationResult> results = deserializeFiles(fileBytes, message);
            loadDeserializationResults(results, message);
            this.loadedFiles.addAllIterable(fileBytes.keysView());
        }
    }

    private ListIterable<SourceDeserializationResult> deserializeFiles(MapIterable<String, byte[]> fileBytes, Message message)
    {
        int fileCount = fileBytes.size();
        if (message != null)
        {
            message.setMessage(String.format("    Deserializing %,d files ...", fileCount));
        }
        ExternalReferenceSerializerLibrary serializerLibrary = ExternalReferenceSerializerLibrary.newLibrary(this.parserLibrary);
        ReferenceFactory referenceFactory = CachedReferenceFactory.wrap(new SimpleReferenceFactory());
        Function<byte[], SourceDeserializationResult> deserialize = sourceBytes -> BinaryModelSourceDeserializer.deserialize(BinaryReaders.newBinaryReader(sourceBytes), serializerLibrary, referenceFactory, true, false, false);
        ListIterable<SourceDeserializationResult> results;
        if (shouldParallelize(fileCount, DESERIALIZE_FILES_THRESHOLD))
        {
            results = ForkJoinTools.collect(this.forkJoinPool, Lists.mutable.<byte[]>withInitialCapacity(fileCount).withAll(fileBytes.valuesView()), deserialize, DESERIALIZE_FILES_THRESHOLD);
        }
        else
        {
            results = fileBytes.valuesView().collect(deserialize, Lists.mutable.withInitialCapacity(fileCount));
        }
        return results;
    }

    private void loadDeserializationResults(ListIterable<SourceDeserializationResult> results, Message message)
    {
        ListIterable<DeserializationNode> nodes = LazyIterate.select(results, SourceDeserializationResult::hasDeserializationNodes).flatCollect(SourceDeserializationResult::getDeserializationNodes, Lists.mutable.empty());
        initializeNodes(nodes, message);
        resolveReferences(nodes, message);

        ListIterable<CoreInstance> instances = nodes.collect(DeserializationNode::getInstance);
        populateBackReferences(instances, message);
        updateContext(instances, message);
        updateSourceRegistry(results, nodes, message);
        updatePatternLibrary(nodes, message);
    }

    private void initializeNodes(ListIterable<DeserializationNode> nodes, Message message)
    {
        if (message != null)
        {
            message.setMessage(String.format("    Initializing %,d nodes ...", nodes.size()));
        }
        nodes.forEach(this::initializeTopLevelNode);
        if (shouldParallelize(nodes.size(), INITIALIZE_NODES_THRESHOLD))
        {
            ForkJoinTools.forEach(this.forkJoinPool, nodes, this::initializeNonTopLevelNode, INITIALIZE_NODES_THRESHOLD);
        }
        else
        {
            nodes.forEach(this::initializeNonTopLevelNode);
        }
    }

    private void initializeTopLevelNode(DeserializationNode node)
    {
        if (node.isTopLevel())
        {
            node.initializeInstance(this.repository, this.processorSupport);
        }
    }

    private void initializeNonTopLevelNode(DeserializationNode node)
    {
        if (!node.isTopLevel())
        {
            node.initializeInstance(this.repository, this.processorSupport);
        }
    }

    private void resolveReferences(ListIterable<DeserializationNode> nodes, Message message)
    {
        for (int passCount = 1; true; passCount++)
        {
            if (message != null)
            {
                message.setMessage("    Resolving references, pass " + passCount + " ...");
            }
            int newlyResolvedCount;
            int unresolvedCount;
            if (shouldParallelize(nodes.size(), RESOLVE_REFERENCES_THRESHOLD))
            {
                ReferenceResolutionResult resolutionResult = this.forkJoinPool.invoke(new RecursiveResolveReferencesTask(nodes, this.repository, this.processorSupport));
                newlyResolvedCount = resolutionResult.getNewlyResolved();
                unresolvedCount = resolutionResult.getUnresolved();
                if (newlyResolvedCount > 0)
                {
                    ForkJoinTools.forEach(this.forkJoinPool, nodes, DeserializationNode::populateResolvedProperties, RESOLVE_REFERENCES_THRESHOLD);
                }
            }
            else
            {
                newlyResolvedCount = 0;
                unresolvedCount = 0;
                for (DeserializationNode node : nodes)
                {
                    ReferenceResolutionResult resolutionResult = node.resolveReferences(this.repository, this.processorSupport);
                    node.populateResolvedProperties();
                    newlyResolvedCount += resolutionResult.getNewlyResolved();
                    unresolvedCount += resolutionResult.getUnresolved();
                }
            }
            if (unresolvedCount == 0)
            {
                // Done
                return;
            }
            if (newlyResolvedCount == 0)
            {
                // Not done, but no progress was made
                StringBuilder errorMessage = new StringBuilder("Failed to resolve nodes after ").append(passCount).append(" passes; ");
                MutableList<Reference> unresolved = Lists.mutable.withInitialCapacity(unresolvedCount);
                nodes.forEach(n -> n.collectUnresolvedReferences(unresolved));
                if (unresolvedCount == 1)
                {
                    errorMessage.append("1 node remains unresolved");
                    String failureMessage = unresolved.get(0).getFailureMessage();
                    if (failureMessage != null)
                    {
                        errorMessage.append(": ").append(failureMessage);
                    }
                }
                else
                {
                    errorMessage.append(unresolvedCount).append(" nodes remain unresolved");
                    int messagesDisplayed = 0;
                    for (Reference reference : unresolved)
                    {
                        String failureMessage = reference.getFailureMessage();
                        if (failureMessage != null)
                        {
                            messagesDisplayed++;
                            errorMessage.append("\n\t").append(messagesDisplayed).append(": ").append(failureMessage);
                            if (messagesDisplayed >= 10)
                            {
                                break;
                            }
                        }
                    }
                    if (messagesDisplayed == 0)
                    {
                        errorMessage.append(" (no failure messages to display)");
                    }
                    else if (messagesDisplayed < unresolvedCount)
                    {
                        errorMessage.append("\n\t").append(unresolvedCount - messagesDisplayed).append(" more failure messages not displayed ...");
                    }
                }
                throw new RuntimeException(errorMessage.toString());
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private void populateBackReferences(ListIterable<CoreInstance> instances, Message message)
    {
        if (message != null)
        {
            message.setMessage("    Populating reverse references (" + instances.size() + " instances)...");
        }

        MapIterable<CoreInstance, ? extends ListIterable<Processor>> processorsByType = getProcessorsByType();

        CoreInstance annotatedElementClass = getByUserPath(M3Paths.AnnotatedElement);
        CoreInstance associationClass = getByUserPath(M3Paths.Association);
        CoreInstance functionDefinitionClass = getByUserPath(M3Paths.FunctionDefinition);
        CoreInstance functionExpressionClass = getByUserPath(M3Paths.FunctionExpression);
        CoreInstance newPropertyRouteNodeFunctionDefinition = getByUserPath(M3Paths.NewPropertyRouteNodeFunctionDefinition);
        CoreInstance typeClass = getByUserPath(M3Paths.Type);

        BackReferencePopulator backReferencePopulator = new BackReferencePopulator(this.repository, this.context, this.processorSupport, processorsByType, annotatedElementClass, associationClass, functionDefinitionClass, functionExpressionClass, newPropertyRouteNodeFunctionDefinition, typeClass);
        if (shouldParallelize(instances.size(), POPULATE_BACK_REFERENCES_THRESHOLD))
        {
            ForkJoinTools.forEach(this.forkJoinPool, instances, backReferencePopulator, POPULATE_BACK_REFERENCES_THRESHOLD);
        }
        else
        {
            instances.forEach(backReferencePopulator);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private MapIterable<CoreInstance, ? extends ListIterable<Processor>> getProcessorsByType()
    {
        MutableMap<CoreInstance, MutableList<Processor>> processorsByType = Maps.mutable.empty();
        RichIterable<Processor> processors = LazyIterate.concatenate(
                        this.parserLibrary.getParsers().asLazy().flatCollect(Parser::getProcessors),
                        this.inlineDSLLibrary.getInlineDSLs().asLazy().flatCollect(InlineDSL::getProcessors))
                .selectInstancesOf(Processor.class);
        processors.forEach(processor ->
        {
            CoreInstance type = getByUserPath(processor.getClassName());
            // Type may not be loaded yet, and that is ok.
            if (type != null)
            {
                processorsByType.getIfAbsentPut(type, Lists.mutable::empty).add(processor);
            }
        });
        return processorsByType;
    }

    private void updateContext(ListIterable<CoreInstance> instances, Message message)
    {
        if (message != null)
        {
            message.setMessage("    Updating context ...");
        }
        if (shouldParallelize(instances.size(), UPDATE_CONTEXT_THRESHOLD))
        {
            ForkJoinTools.forEach(this.forkJoinPool, instances, this::updateContext, UPDATE_CONTEXT_THRESHOLD);
        }
        else
        {
            instances.forEach(this::updateContext);
        }
    }

    private void updateContext(CoreInstance instance)
    {
        this.context.update(instance);
        this.context.registerInstanceByClassifier(instance);
        this.context.registerFunctionByName(instance);
    }

    private void updateSourceRegistry(RichIterable<SourceDeserializationResult> results, RichIterable<DeserializationNode> nodes, Message message)
    {
        if (message != null)
        {
            message.setMessage("    Updating source registry ...");
        }

        // Index top level and packaged instances by path
        MutableMap<String, CoreInstance> instancesByPath = Maps.mutable.empty();
        nodes.forEach(node ->
        {
            if (node.isTopLevel() || node.isPackaged())
            {
                CoreInstance instance = node.getInstance();
                String path = PackageableElement.getUserPathForPackageableElement(instance);
                instancesByPath.put(path, instance);
            }
        });

        // Update source registry
        results.forEach(result ->
        {
            MutableListMultimap<Parser, CoreInstance> instancesByParser = Multimaps.mutable.list.empty();
            try
            {
                result.getInstancesByParser().forEachKeyMultiValues((parserName, instancePaths) ->
                {
                    Parser parser = this.parserLibrary.getParser(parserName);
                    instancePaths.forEach(instancePath ->
                    {
                        CoreInstance instance = instancesByPath.get(instancePath);
                        if (instance == null)
                        {
                            throw new RuntimeException("Could not find instance '" + instancePath + "'");
                        }
                        instancesByParser.put(parser, instance);
                    });
                });
            }
            catch (Exception e)
            {
                StringBuilder builder = new StringBuilder("Error updating ").append(result.getSource().getId());
                String eMessage = e.getMessage();
                if (eMessage != null)
                {
                    builder.append(": ").append(eMessage);
                }
                throw new RuntimeException(builder.toString(), e);
            }

            Source source = result.getSource();
            source.linkInstances(instancesByParser);
            source.setCompiled(true);
            this.sourceRegistry.registerSource(result.getSource());
        });
    }

    private void updatePatternLibrary(RichIterable<DeserializationNode> nodes, Message message)
    {
        if (message != null)
        {
            message.setMessage("    Updating pattern library ...");
        }

        if (this.patternLibrary != null)
        {
            // Update URL pattern library
            nodes.forEach(node ->
            {
                if (node.isPackaged())
                {
                    this.patternLibrary.possiblyRegister(node.getInstance(), this.processorSupport);
                }
            });
        }
    }

    private CoreInstance getByUserPath(String path)
    {
        return this.processorSupport.package_getByUserPath(path);
    }

    private boolean shouldParallelize(int size, int threshold)
    {
        return (this.forkJoinPool != null) && (size > threshold);
    }

    private static String convertToBinaryPath(String path)
    {
        return CodeStorageTools.isPureFilePath(path) ? PureRepositoryJarTools.purePathToBinaryPath(path) : path;
    }

    private static class RecursiveResolveReferencesTask extends RecursiveTask<ReferenceResolutionResult>
    {
        private final int start;
        private final int end;
        private final ListIterable<DeserializationNode> nodes;
        private final ModelRepository repository;
        private final ProcessorSupport processorSupport;

        private RecursiveResolveReferencesTask(int start, int end, ListIterable<DeserializationNode> nodes, ModelRepository repository, ProcessorSupport processorSupport)
        {
            this.start = start;
            this.end = end;
            this.nodes = nodes;
            this.repository = repository;
            this.processorSupport = processorSupport;
        }

        private RecursiveResolveReferencesTask(ListIterable<DeserializationNode> nodes, ModelRepository repository, ProcessorSupport processorSupport)
        {
            this(0, nodes.size(), nodes, repository, processorSupport);
        }

        @Override
        protected ReferenceResolutionResult compute()
        {
            int size = this.end - this.start;
            if (size <= RESOLVE_REFERENCES_THRESHOLD)
            {
                int newlyResolvedCount = 0;
                int unresolvedCount = 0;
                for (int i = this.start; i < this.end; i++)
                {
                    ReferenceResolutionResult result = this.nodes.get(i).resolveReferences(this.repository, this.processorSupport);
                    newlyResolvedCount += result.getNewlyResolved();
                    unresolvedCount += result.getUnresolved();
                }
                return new ReferenceResolutionResult(newlyResolvedCount, unresolvedCount);
            }
            else
            {
                int midPoint = this.start + (size / 2);
                RecursiveResolveReferencesTask task1 = new RecursiveResolveReferencesTask(this.start, midPoint, this.nodes, this.repository, this.processorSupport);
                RecursiveResolveReferencesTask task2 = new RecursiveResolveReferencesTask(midPoint, this.end, this.nodes, this.repository, this.processorSupport);
                invokeAll(task1, task2);
                ReferenceResolutionResult result1 = task1.getRawResult();
                ReferenceResolutionResult result2 = task2.getRawResult();
                return result1.join(result2);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static class BackReferencePopulator implements Procedure<CoreInstance>
    {
        private final ModelRepository repository;
        private final Context context;
        private final ProcessorSupport processorSupport;
        private final MapIterable<CoreInstance, ? extends ListIterable<Processor>> processorsByType;
        private final CoreInstance annotatedElementClass;
        private final CoreInstance associationClass;
        private final CoreInstance functionDefinitionClass;
        private final CoreInstance functionExpressionClass;
        private final CoreInstance newPropertyRouteNodeFunctionDefinition;
        private final CoreInstance typeClass;

        private BackReferencePopulator(ModelRepository repository, Context context, ProcessorSupport processorSupport, MapIterable<CoreInstance, ? extends ListIterable<Processor>> processorsByType, CoreInstance annotatedElementClass, CoreInstance associationClass, CoreInstance functionDefinitionClass, CoreInstance functionExpressionClass, CoreInstance newPropertyRouteNodeFunctionDefinition, CoreInstance typeClass)
        {
            this.repository = repository;
            this.context = context;
            this.processorSupport = processorSupport;
            this.processorsByType = processorsByType;
            this.annotatedElementClass = annotatedElementClass;
            this.associationClass = associationClass;
            this.functionDefinitionClass = functionDefinitionClass;
            this.functionExpressionClass = functionExpressionClass;
            this.newPropertyRouteNodeFunctionDefinition = newPropertyRouteNodeFunctionDefinition;
            this.typeClass = typeClass;
        }

        @Override
        public void value(CoreInstance instance)
        {
            try
            {
                ListIterable<CoreInstance> genlsList = Type.getGeneralizationResolutionOrder(instance.getClassifier(), this.processorSupport);
                SetIterable<CoreInstance> genlsSet = genlsList.toSet();

                // applications
                if (genlsSet.contains(this.functionExpressionClass))
                {
                    CoreInstance function = Instance.getValueForMetaPropertyToOneResolved(instance, M3Properties.func, this.processorSupport);
                    function.addKeyValue(M3PropertyPaths.applications, instance);
                }

                // modelElements
                if (genlsSet.contains(this.annotatedElementClass))
                {
                    AnnotatedElementProcessor.noteModelElementForAnnotations(instance, this.processorSupport);
                }

                if (genlsSet.contains(this.associationClass))
                {
                    // propertiesFromAssociations
                    instance.getValueForMetaPropertyToMany(M3Properties.properties).forEach(property ->
                    {
                        CoreInstance sourceType = Property.getSourceType(property, this.processorSupport);
                        sourceType.addKeyValue(M3PropertyPaths.propertiesFromAssociations, property);
                        this.context.update(sourceType);
                    });

                    // qualifiedPropertiesFromAssociations
                    instance.getValueForMetaPropertyToMany(M3Properties.qualifiedProperties).forEach(property ->
                    {
                        CoreInstance sourceType = Property.getSourceType(property, this.processorSupport);
                        sourceType.addKeyValue(M3PropertyPaths.qualifiedPropertiesFromAssociations, property);
                        this.context.update(sourceType);
                    });
                }

                // specializations
                if (genlsSet.contains(this.typeClass))
                {
                    SpecializationProcessor.process((org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Type) instance, this.processorSupport);
                }

                // referenceUsages
                if (instance.hasBeenProcessed())
                {
                    genlsList.asReversed().forEach(type ->
                    {
                        ListIterable<Processor> processors = this.processorsByType.get(type);
                        if (processors != null)
                        {
                            processors.forEach(p -> p.populateReferenceUsages(instance, this.repository, this.processorSupport));
                        }
                    });
                    // TODO find a better way to do this
                    if (genlsSet.contains(this.functionDefinitionClass) && !genlsSet.contains(this.newPropertyRouteNodeFunctionDefinition))
                    {
                        GenericTypeTraceability.addTraceForFunctionDefinition((FunctionDefinition<?>) instance, this.repository, this.processorSupport);
                    }
                }
            }
            catch (Exception e)
            {
                StringBuilder errorMessage = new StringBuilder("Error populating back reference properties for ").append(instance);
                SourceInformation sourceInfo = instance.getSourceInformation();
                if (sourceInfo != null)
                {
                    sourceInfo.appendMessage(errorMessage.append(" (")).append(')');
                }
                throw new RuntimeException(errorMessage.toString(), e);
            }
        }
    }

    public static MutableList<PureRepositoryJar> findJars(Iterable<String> repoNames, ClassLoader classLoader, Message message)
    {
        return findJars(repoNames, classLoader, message, true);
    }

    public static MutableList<PureRepositoryJar> findJars(Iterable<String> repoNames, ClassLoader classLoader, Message message, boolean errorOnNotFound)
    {
        MutableList<PureRepositoryJar> jars = Lists.mutable.empty();
        repoNames.forEach(repoName ->
        {
            PureRepositoryJar jar = findJar(repoName, classLoader, message, errorOnNotFound);
            if (jar != null)
            {
                jars.add(jar);
            }
        });
        return jars;
    }

    private static PureRepositoryJar findJar(String repoName, ClassLoader classLoader, Message message, boolean errorOnFailure)
    {
        String resourceName = "pure-" + repoName + ".par";
        URL url = classLoader.getResource(resourceName);
        if (url == null)
        {
            if (errorOnFailure)
            {
                throw new RuntimeException("Could not find resource: " + resourceName);
            }
            if (message != null)
            {
                message.setMessage("  Could not find " + resourceName);
            }
            return null;
        }

        if (message != null)
        {
            message.setMessage("  Found " + resourceName + " at " + url);
        }
        return PureRepositoryJars.get(url);
    }
}

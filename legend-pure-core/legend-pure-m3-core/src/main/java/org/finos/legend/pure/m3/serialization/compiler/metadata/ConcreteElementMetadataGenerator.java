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

package org.finos.legend.pure.m3.serialization.compiler.metadata;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.Referenceable;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Annotation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.Function;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Any;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Type;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m3.navigation.graph.GraphPathIterable;
import org.finos.legend.pure.m3.navigation.graph.ResolvedGraphPath;
import org.finos.legend.pure.m3.navigation.imports.Imports;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProvider;
import org.finos.legend.pure.m4.coreinstance.AbstractCoreInstanceWrapper;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
import org.finos.legend.pure.m4.tools.GraphWalkFilterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class ConcreteElementMetadataGenerator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteElementMetadataGenerator.class);

    private final ImmutableMap<String, ImmutableList<String>> BACK_REFERENCE_PROPERTIES = M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.groupByUniqueKey(ImmutableList::getLast);

    private final ReferenceIdProvider referenceIdProvider;
    private final ProcessorSupport processorSupport;

    ConcreteElementMetadataGenerator(ReferenceIdProvider referenceIdProvider, ProcessorSupport processorSupport)
    {
        this.referenceIdProvider = referenceIdProvider;
        this.processorSupport = processorSupport;
    }

    public ConcreteElementMetadata generateMetadata(CoreInstance concreteElement)
    {
        long start = System.nanoTime();
        if (!PackageableElement.isPackageableElement(concreteElement, this.processorSupport))
        {
            throw new IllegalArgumentException("Not a PackageableElement: " + concreteElement);
        }

        String elementPath = PackageableElement.getUserPathForPackageableElement(concreteElement);
        LOGGER.debug("Generating metadata for {}", elementPath);
        try
        {

            SourceInformation sourceInfo = concreteElement.getSourceInformation();
            if (sourceInfo == null)
            {
                throw new IllegalArgumentException("Missing source information for " + elementPath);
            }

            CoreInstance classifier = this.processorSupport.getClassifier(concreteElement);
            if (classifier == null)
            {
                throw new IllegalArgumentException("Cannot get classifier for " + elementPath);
            }

            ConcreteElementMetadata.Builder builder = ConcreteElementMetadata.builder()
                    .withPath(elementPath)
                    .withClassifierPath(PackageableElement.getUserPathForPackageableElement(classifier))
                    .withSourceInformation(sourceInfo)
                    .withReferenceIdVersion(this.referenceIdProvider.version());
            return computeExternalReferences(concreteElement, builder).build();
        }
        catch (Throwable t)
        {
            LOGGER.debug("Error generating metadata for {}", elementPath, t);
            throw t;
        }
        finally
        {
            long end = System.nanoTime();
            LOGGER.debug("Finished generating metadata for {} in {}s", elementPath, (end - start) / 1_000_000_000.0);
        }
    }

    private ConcreteElementMetadata.Builder computeExternalReferences(CoreInstance concreteElement, ConcreteElementMetadata.Builder builder)
    {
        MutableSet<CoreInstance> internalNodes = Sets.mutable.empty();
        MutableList<CoreInstance> externalNodes = Lists.mutable.empty();
        GraphNodeIterable.builder()
                .withStartingNode(concreteElement)
                .withKeyFilter(this::propertyFilter)
                .withNodeFilter(node -> isExternal(concreteElement, node) ? GraphWalkFilterResult.get(!Imports.isImportGroup(node, this.processorSupport), false) : GraphWalkFilterResult.ACCEPT_AND_CONTINUE)
                .build()
                .forEach(node -> (isExternal(concreteElement, node) ? externalNodes : internalNodes).add(node));
        LOGGER.debug("{} has {} internal nodes and {} external references", builder.getPath(), internalNodes.size(), externalNodes.size());
        boolean elementIsAssociation = isAssociation(concreteElement);
        externalNodes.forEach(externalNode ->
        {
            String externalNodeId = this.referenceIdProvider.getReferenceId(externalNode);
            ExternalReference.Builder extRefBuilder = ExternalReference.builder().withReferenceId(externalNodeId);
            if (isFunction(externalNode))
            {
                externalNode.getValueForMetaPropertyToMany(M3Properties.applications).asLazy()
                        .select(internalNodes::contains)
                        .collect(fe -> getReferenceIdForInternalNode(fe, concreteElement, externalNodeId, M3Properties.applications))
                        .forEach(extRefBuilder::withApplication);
            }
            if (isAnnotation(externalNode))
            {
                externalNode.getValueForMetaPropertyToMany(M3Properties.modelElements).asLazy()
                        .select(internalNodes::contains)
                        .collect(e -> getReferenceIdForInternalNode(e, concreteElement, externalNodeId, M3Properties.modelElements))
                        .forEach(extRefBuilder::withModelElement);
            }
            if (elementIsAssociation && isClass(externalNode))
            {
                externalNode.getValueForMetaPropertyToMany(M3Properties.propertiesFromAssociations).asLazy()
                        .select(internalNodes::contains)
                        .collect(p -> getReferenceIdForInternalNode(p, concreteElement, externalNodeId, M3Properties.propertiesFromAssociations))
                        .forEach(extRefBuilder::withPropertyFromAssociation);
                externalNode.getValueForMetaPropertyToMany(M3Properties.qualifiedPropertiesFromAssociations).asLazy()
                        .select(internalNodes::contains)
                        .collect(qp -> getReferenceIdForInternalNode(qp, concreteElement, externalNodeId, M3Properties.qualifiedPropertiesFromAssociations))
                        .forEach(extRefBuilder::withQualifiedPropertyFromAssociation);
            }
            if (isReferenceable(externalNode))
            {
                externalNode.getValueForMetaPropertyToMany(M3Properties.referenceUsages).forEach(refUsage ->
                {
                    CoreInstance owner = refUsage.getValueForMetaPropertyToOne(M3Properties.owner);
                    if (internalNodes.contains(owner))
                    {
                        String ownerId = getReferenceIdForInternalNode(owner, concreteElement, externalNodeId, M3Properties.referenceUsages);
                        String propertyName = PrimitiveUtilities.getStringValue(refUsage.getValueForMetaPropertyToOne(M3Properties.propertyName));
                        int offset = PrimitiveUtilities.getIntegerValue(refUsage.getValueForMetaPropertyToOne(M3Properties.offset)).intValue();
                        extRefBuilder.withReferenceUsage(ownerId, propertyName, offset);
                    }
                });
            }
            if (isType(externalNode))
            {
                externalNode.getValueForMetaPropertyToMany(M3Properties.specializations).asLazy()
                        .select(internalNodes::contains)
                        .collect(s -> getReferenceIdForInternalNode(s, concreteElement, externalNodeId, M3Properties.specializations))
                        .forEach(extRefBuilder::withSpecialization);
            }
            builder.withExternalReference(extRefBuilder.build());
        });
        return builder;
    }

    private boolean isExternal(CoreInstance concreteElement, CoreInstance node)
    {
        if (concreteElement == node)
        {
            return false;
        }

        SourceInformation sourceInfo = node.getSourceInformation();
        return (sourceInfo == null) ? _Package.isPackage(node, this.processorSupport) : !concreteElement.getSourceInformation().subsumes(sourceInfo);
    }

    private boolean isAnnotation(CoreInstance instance)
    {
        return isInstanceOf(instance, Annotation.class, M3Paths.Annotation);
    }

    private boolean isAssociation(CoreInstance instance)
    {
        return isInstanceOf(instance, Association.class, M3Paths.Association);
    }

    private boolean isClass(CoreInstance instance)
    {
        return isInstanceOf(instance, Class.class, M3Paths.Class);
    }

    private boolean isFunction(CoreInstance instance)
    {
        return isInstanceOf(instance, Function.class, M3Paths.Function);
    }

    private boolean isReferenceable(CoreInstance instance)
    {
        return isInstanceOf(instance, Referenceable.class, M3Paths.Referenceable);
    }

    private boolean isType(CoreInstance instance)
    {
        return isInstanceOf(instance, Type.class, M3Paths.Type);
    }

    private boolean isInstanceOf(CoreInstance instance, java.lang.Class<? extends Any> javaClass, String pureClassPath)
    {
        return javaClass.isInstance(instance) ||
                ((!(instance instanceof Any) || (instance instanceof AbstractCoreInstanceWrapper)) && this.processorSupport.instance_instanceOf(instance, pureClassPath));
    }

    private boolean propertyFilter(CoreInstance node, String property)
    {
        ImmutableList<String> propertyPath = BACK_REFERENCE_PROPERTIES.get(property);
        return (propertyPath == null) || !propertyPath.equals(node.getRealKeyByName(property));
    }

    private String getReferenceIdForInternalNode(CoreInstance internalNode, CoreInstance concreteElement, String externalNodeId, String backRefProperty)
    {
        try
        {
            return this.referenceIdProvider.getReferenceId(internalNode);
        }
        catch (Exception e)
        {
            StringBuilder message = new StringBuilder("Error computing ").append(backRefProperty).append(" for ").append(externalNodeId).append(" from ");
            PackageableElement.writeUserPathForPackageableElement(message, concreteElement);
            tryFindPathToInternalNode(concreteElement, internalNode).ifPresent(path -> path.writeDescription(message.append(": error computing reference id for instance at ")));
            throw new RuntimeException(message.toString(), e);
        }
    }

    private Optional<GraphPath> tryFindPathToInternalNode(CoreInstance concreteElement, CoreInstance internalNode)
    {
        try
        {
            MutableSet<CoreInstance> visited = Sets.mutable.empty();
            return Optional.ofNullable(GraphPathIterable.builder(this.processorSupport)
                    .withStartNode(concreteElement)
                    .withPropertyFilter((rgp, property) -> propertyFilter(rgp.getLastResolvedNode(), property))
                    .withPathFilter(rgp ->
                    {
                        CoreInstance node = rgp.getLastResolvedNode();
                        if (node == internalNode)
                        {
                            return GraphWalkFilterResult.ACCEPT_AND_STOP;
                        }
                        if (isExternal(concreteElement, node))
                        {
                            return GraphWalkFilterResult.REJECT_AND_STOP;
                        }
                        return GraphWalkFilterResult.get(false, visited.add(node));
                    })
                    .build()
                    .getAny())
                    .map(ResolvedGraphPath::getGraphPath);
        }
        catch (Exception ignore)
        {
            return Optional.empty();
        }
    }
}

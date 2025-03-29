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
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.SetIterable;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
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
    private static final ImmutableMap<String, ImmutableList<String>> BACK_REFERENCE_PROPERTIES = M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.groupByUniqueKey(ImmutableList::getLast);

    private final ReferenceIdProvider referenceIdProvider;
    private final ProcessorSupport processorSupport;
    private final ConcurrentMutableMap<CoreInstance, ExternalReferenceBuilder> extRefBuilderCache = ConcurrentHashMap.newMap();

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
                .withNodeFilter(node -> isExternal(concreteElement, node) ? GraphWalkFilterResult.stop(!Imports.isImportGroup(node, this.processorSupport)) : GraphWalkFilterResult.ACCEPT_AND_CONTINUE)
                .build()
                .forEach(node -> (isExternal(concreteElement, node) ? externalNodes : internalNodes).add(node));
        LOGGER.debug("{} has {} internal nodes and {} external references", builder.getPath(), internalNodes.size(), externalNodes.size());
        boolean elementIsAssociation = isAssociation(concreteElement);
        MutableMap<CoreInstance, String> idCache = Maps.mutable.empty();
        externalNodes.forEach(externalNode -> builder.withExternalReference(getExternalReferenceBuilder(externalNode).build(concreteElement, internalNodes, idCache, elementIsAssociation)));
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

    private String getReferenceIdForInternalNode(CoreInstance internalNode, MutableMap<CoreInstance, String> cache, CoreInstance concreteElement, String externalNodeId, String backRefProperty)
    {
        return cache.getIfAbsentPut(internalNode, () -> getReferenceIdForInternalNode(internalNode, concreteElement, externalNodeId, backRefProperty));
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

    private ExternalReferenceBuilder getExternalReferenceBuilder(CoreInstance extRef)
    {
        ExternalReferenceBuilder cached = this.extRefBuilderCache.get(extRef);
        if (cached != null)
        {
            return cached;
        }

        ListIterable<? extends CoreInstance> functionApplications = isFunction(extRef) ? extRef.getValueForMetaPropertyToMany(M3Properties.applications) : Lists.immutable.empty();
        ListIterable<? extends CoreInstance> modelElements = isAnnotation(extRef) ? extRef.getValueForMetaPropertyToMany(M3Properties.modelElements) : Lists.immutable.empty();
        ListIterable<? extends CoreInstance> propertiesFromAssociations;
        ListIterable<? extends CoreInstance> qualifiedPropertiesFromAssociations;
        ListIterable<? extends CoreInstance> referenceUsages;
        ListIterable<? extends CoreInstance> specializations;
        if (isClass(extRef))
        {
            propertiesFromAssociations = extRef.getValueForMetaPropertyToMany(M3Properties.propertiesFromAssociations);
            qualifiedPropertiesFromAssociations = extRef.getValueForMetaPropertyToMany(M3Properties.qualifiedPropertiesFromAssociations);
            referenceUsages = extRef.getValueForMetaPropertyToMany(M3Properties.referenceUsages);
            specializations = extRef.getValueForMetaPropertyToMany(M3Properties.specializations);
        }
        else
        {
            propertiesFromAssociations = Lists.immutable.empty();
            qualifiedPropertiesFromAssociations = Lists.immutable.empty();
            referenceUsages = isReferenceable(extRef) ? extRef.getValueForMetaPropertyToMany(M3Properties.referenceUsages) : Lists.immutable.empty();
            specializations = isType(extRef) ? extRef.getValueForMetaPropertyToMany(M3Properties.specializations) : Lists.immutable.empty();
        }

        if ((shouldCache(functionApplications) || shouldCache(modelElements) || shouldCache(propertiesFromAssociations) || shouldCache(qualifiedPropertiesFromAssociations) || shouldCache(referenceUsages) || shouldCache(specializations)))
        {
            String extRefId = this.referenceIdProvider.getReferenceId(extRef);
            LOGGER.debug("Caching external reference builder for {}", extRefId);
            return this.extRefBuilderCache.getIfAbsentPut(extRef, () -> new CachedExternalReferenceBuilder(extRefId, functionApplications, modelElements, propertiesFromAssociations, qualifiedPropertiesFromAssociations, referenceUsages, specializations));
        }
        return new SimpleExternalReferenceBuilder(this.referenceIdProvider.getReferenceId(extRef), functionApplications, modelElements, propertiesFromAssociations, qualifiedPropertiesFromAssociations, referenceUsages, specializations);
    }

    private boolean shouldCache(ListIterable<? extends CoreInstance> values)
    {
        return values.size() >= 100;
    }

    private abstract static class ExternalReferenceBuilder
    {
        final String externalRefId;

        private ExternalReferenceBuilder(String externalRefId)
        {
            this.externalRefId = externalRefId;
        }

        ExternalReference build(CoreInstance concreteElement, SetIterable<? extends CoreInstance> internalNodes, MutableMap<CoreInstance, String> idCache, boolean elementIsAssociation)
        {
            ExternalReference.Builder builder = ExternalReference.builder().withReferenceId(this.externalRefId);
            collectBackReferences(concreteElement, internalNodes, idCache, elementIsAssociation, builder);
            return builder.build();
        }

        abstract void collectBackReferences(CoreInstance concreteElement, SetIterable<? extends CoreInstance> internalNodes, MutableMap<CoreInstance, String> idCache, boolean elementIsAssociation, ExternalReference.Builder builder);
    }

    private class SimpleExternalReferenceBuilder extends ExternalReferenceBuilder
    {
        private final ListIterable<? extends CoreInstance> functionApplications;
        private final ListIterable<? extends CoreInstance> modelElements;
        private final ListIterable<? extends CoreInstance> propertiesFromAssociations;
        private final ListIterable<? extends CoreInstance> qualifiedPropertiesFromAssociations;
        private final ListIterable<? extends CoreInstance> referenceUsages;
        private final ListIterable<? extends CoreInstance> specializations;

        private SimpleExternalReferenceBuilder(String externalRefId,
                                               ListIterable<? extends CoreInstance> functionApplications,
                                               ListIterable<? extends CoreInstance> modelElements,
                                               ListIterable<? extends CoreInstance> propertiesFromAssociations,
                                               ListIterable<? extends CoreInstance> qualifiedPropertiesFromAssociations,
                                               ListIterable<? extends CoreInstance> referenceUsages,
                                               ListIterable<? extends CoreInstance> specializations)
        {
            super(externalRefId);
            this.functionApplications = functionApplications;
            this.modelElements = modelElements;
            this.propertiesFromAssociations = propertiesFromAssociations;
            this.qualifiedPropertiesFromAssociations = qualifiedPropertiesFromAssociations;
            this.referenceUsages = referenceUsages;
            this.specializations = specializations;
        }

        @Override
        void collectBackReferences(CoreInstance concreteElement, SetIterable<? extends CoreInstance> internalNodes, MutableMap<CoreInstance, String> idCache, boolean elementIsAssociation, ExternalReference.Builder builder)
        {
            if (this.functionApplications.notEmpty())
            {
                this.functionApplications.forEach(fe ->
                {
                    if (internalNodes.contains(fe))
                    {
                        builder.withApplication(getReferenceIdForInternalNode(fe, idCache, concreteElement, this.externalRefId, M3Properties.applications));
                    }
                });
            }
            if (this.modelElements.notEmpty())
            {
                this.modelElements.forEach(e ->
                {
                    if (internalNodes.contains(e))
                    {
                        builder.withModelElement(getReferenceIdForInternalNode(e, idCache, concreteElement, this.externalRefId, M3Properties.modelElements));
                    }
                });
            }
            if (elementIsAssociation)
            {
                if (this.propertiesFromAssociations.notEmpty())
                {
                    this.propertiesFromAssociations.forEach(p ->
                    {
                        if (internalNodes.contains(p))
                        {
                            builder.withPropertyFromAssociation(getReferenceIdForInternalNode(p, idCache, concreteElement, this.externalRefId, M3Properties.propertiesFromAssociations));
                        }
                    });
                }
                if (this.qualifiedPropertiesFromAssociations.notEmpty())
                {
                    this.qualifiedPropertiesFromAssociations.forEach(qp ->
                    {
                        if (internalNodes.contains(qp))
                        {
                            builder.withQualifiedPropertyFromAssociation(getReferenceIdForInternalNode(qp, idCache, concreteElement, this.externalRefId, M3Properties.qualifiedPropertiesFromAssociations));
                        }
                    });
                }
            }
            if (this.referenceUsages.notEmpty())
            {
                this.referenceUsages.forEach(refUsage ->
                {
                    CoreInstance owner = refUsage.getValueForMetaPropertyToOne(M3Properties.owner);
                    if (internalNodes.contains(owner))
                    {
                        String ownerId = getReferenceIdForInternalNode(owner, idCache, concreteElement, this.externalRefId, M3Properties.referenceUsages);
                        String propertyName = PrimitiveUtilities.getStringValue(refUsage.getValueForMetaPropertyToOne(M3Properties.propertyName));
                        int offset = PrimitiveUtilities.getIntegerValue(refUsage.getValueForMetaPropertyToOne(M3Properties.offset)).intValue();
                        builder.withReferenceUsage(ownerId, propertyName, offset);
                    }
                });
            }
            if (this.specializations.notEmpty())
            {
                this.specializations.forEach(s ->
                {
                    if (internalNodes.contains(s))
                    {
                        builder.withSpecialization(getReferenceIdForInternalNode(s, idCache, concreteElement, this.externalRefId, M3Properties.specializations));
                    }
                });
            }
        }
    }

    private class CachedExternalReferenceBuilder extends ExternalReferenceBuilder
    {
        private final SetIterable<CoreInstance> functionApplications;
        private final SetIterable<CoreInstance> modelElements;
        private final SetIterable<CoreInstance> propertiesFromAssociations;
        private final SetIterable<CoreInstance> qualifiedPropertiesFromAssociations;
        private final MapIterable<CoreInstance, ? extends ListIterable<CoreInstance>> referenceUsages;
        private final SetIterable<CoreInstance> specializations;

        private CachedExternalReferenceBuilder(String externalRefId,
                                               ListIterable<? extends CoreInstance> functionApplications,
                                               ListIterable<? extends CoreInstance> modelElements,
                                               ListIterable<? extends CoreInstance> propertiesFromAssociations,
                                               ListIterable<? extends CoreInstance> qualifiedPropertiesFromAssociations,
                                               ListIterable<? extends CoreInstance> referenceUsages,
                                               ListIterable<? extends CoreInstance> specializations)
        {
            super(externalRefId);
            this.functionApplications = functionApplications.isEmpty() ? Sets.immutable.empty() : Sets.mutable.withAll(functionApplications);
            this.modelElements = modelElements.isEmpty() ? Sets.immutable.empty() : Sets.mutable.withAll(modelElements);
            this.propertiesFromAssociations = propertiesFromAssociations.isEmpty() ? Sets.immutable.empty() : Sets.mutable.withAll(propertiesFromAssociations);
            this.qualifiedPropertiesFromAssociations = qualifiedPropertiesFromAssociations.isEmpty() ? Sets.immutable.empty() : Sets.mutable.withAll(qualifiedPropertiesFromAssociations);
            this.referenceUsages = indexRefUsages(referenceUsages);
            this.specializations = specializations.isEmpty() ? Sets.immutable.empty() : Sets.mutable.withAll(specializations);
        }

        @Override
        void collectBackReferences(CoreInstance concreteElement, SetIterable<? extends CoreInstance> internalNodes, MutableMap<CoreInstance, String> idCache, boolean elementIsAssociation, ExternalReference.Builder builder)
        {
            if (this.functionApplications.notEmpty())
            {
                SetIterable<? extends CoreInstance> iterSet;
                SetIterable<? extends CoreInstance> containsSet;
                if (internalNodes.size() > this.functionApplications.size())
                {
                    iterSet = internalNodes;
                    containsSet = this.functionApplications;
                }
                else
                {
                    iterSet = this.functionApplications;
                    containsSet = internalNodes;
                }
                iterSet.forEach(n ->
                {
                    if (containsSet.contains(n))
                    {
                        builder.withApplication(getReferenceIdForInternalNode(n, idCache, concreteElement, this.externalRefId, M3Properties.applications));
                    }
                });
            }
            if (this.modelElements.notEmpty())
            {
                SetIterable<? extends CoreInstance> iterSet;
                SetIterable<? extends CoreInstance> containsSet;
                if (internalNodes.size() > this.modelElements.size())
                {
                    iterSet = internalNodes;
                    containsSet = this.modelElements;
                }
                else
                {
                    iterSet = this.modelElements;
                    containsSet = internalNodes;
                }
                iterSet.forEach(n ->
                {
                    if (containsSet.contains(n))
                    {
                        builder.withModelElement(getReferenceIdForInternalNode(n, idCache, concreteElement, this.externalRefId, M3Properties.modelElements));
                    }
                });
            }
            if (elementIsAssociation)
            {
                if (this.propertiesFromAssociations.notEmpty())
                {
                    SetIterable<? extends CoreInstance> iterSet;
                    SetIterable<? extends CoreInstance> containsSet;
                    if (internalNodes.size() > this.propertiesFromAssociations.size())
                    {
                        iterSet = internalNodes;
                        containsSet = this.propertiesFromAssociations;
                    }
                    else
                    {
                        iterSet = this.propertiesFromAssociations;
                        containsSet = internalNodes;
                    }
                    iterSet.forEach(n ->
                    {
                        if (containsSet.contains(n))
                        {
                            builder.withPropertyFromAssociation(getReferenceIdForInternalNode(n, idCache, concreteElement, this.externalRefId, M3Properties.propertiesFromAssociations));
                        }
                    });
                }
                if (this.qualifiedPropertiesFromAssociations.notEmpty())
                {
                    SetIterable<? extends CoreInstance> iterSet;
                    SetIterable<? extends CoreInstance> containsSet;
                    if (internalNodes.size() > this.qualifiedPropertiesFromAssociations.size())
                    {
                        iterSet = internalNodes;
                        containsSet = this.qualifiedPropertiesFromAssociations;
                    }
                    else
                    {
                        iterSet = this.qualifiedPropertiesFromAssociations;
                        containsSet = internalNodes;
                    }
                    iterSet.forEach(n ->
                    {
                        if (containsSet.contains(n))
                        {
                            builder.withQualifiedPropertyFromAssociation(getReferenceIdForInternalNode(n, idCache, concreteElement, this.externalRefId, M3Properties.qualifiedPropertiesFromAssociations));
                        }
                    });
                }
            }
            if (this.referenceUsages.notEmpty())
            {
                if (internalNodes.size() > this.referenceUsages.size())
                {
                    this.referenceUsages.forEachKeyValue((owner, refUsages) ->
                    {
                        if (internalNodes.contains(owner))
                        {
                            refUsages.forEach(refUsage ->
                            {
                                String ownerId = getReferenceIdForInternalNode(owner, idCache, concreteElement, this.externalRefId, M3Properties.referenceUsages);
                                String propertyName = PrimitiveUtilities.getStringValue(refUsage.getValueForMetaPropertyToOne(M3Properties.propertyName));
                                int offset = PrimitiveUtilities.getIntegerValue(refUsage.getValueForMetaPropertyToOne(M3Properties.offset)).intValue();
                                builder.withReferenceUsage(ownerId, propertyName, offset);
                            });
                        }
                    });
                }
                else
                {
                    internalNodes.forEach(n ->
                    {
                        ListIterable<CoreInstance> refUsages = this.referenceUsages.get(n);
                        if (refUsages != null)
                        {
                            refUsages.forEach(refUsage ->
                            {
                                String ownerId = getReferenceIdForInternalNode(n, idCache, concreteElement, this.externalRefId, M3Properties.referenceUsages);
                                String propertyName = PrimitiveUtilities.getStringValue(refUsage.getValueForMetaPropertyToOne(M3Properties.propertyName));
                                int offset = PrimitiveUtilities.getIntegerValue(refUsage.getValueForMetaPropertyToOne(M3Properties.offset)).intValue();
                                builder.withReferenceUsage(ownerId, propertyName, offset);
                            });
                        }
                    });
                }
            }
            if (this.specializations.notEmpty())
            {
                SetIterable<? extends CoreInstance> iterSet;
                SetIterable<? extends CoreInstance> containsSet;
                if (internalNodes.size() > this.specializations.size())
                {
                    iterSet = internalNodes;
                    containsSet = this.specializations;
                }
                else
                {
                    iterSet = this.specializations;
                    containsSet = internalNodes;
                }
                iterSet.forEach(n ->
                {
                    if (containsSet.contains(n))
                    {
                        builder.withSpecialization(getReferenceIdForInternalNode(n, idCache, concreteElement, this.externalRefId, M3Properties.specializations));
                    }
                });
            }
        }

        private MapIterable<CoreInstance, ? extends ListIterable<CoreInstance>> indexRefUsages(ListIterable<? extends CoreInstance> referenceUsages)
        {
            if (referenceUsages.isEmpty())
            {
                return Maps.immutable.empty();
            }

            MutableMap<CoreInstance, MutableList<CoreInstance>> index = Maps.mutable.empty();
            referenceUsages.forEach(ru -> index.getIfAbsentPut(ru.getValueForMetaPropertyToOne(M3Properties.owner), Lists.mutable::empty).add(ru));
            return index;
        }
    }
}

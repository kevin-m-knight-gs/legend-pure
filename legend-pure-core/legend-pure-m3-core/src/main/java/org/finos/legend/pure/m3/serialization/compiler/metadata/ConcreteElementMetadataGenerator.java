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
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m3.navigation.graph.GraphPathIterable;
import org.finos.legend.pure.m3.navigation.graph.ResolvedGraphPath;
import org.finos.legend.pure.m3.navigation.imports.Imports;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProvider;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.tools.GraphWalkFilterResult;

public class ConcreteElementMetadataGenerator
{
    private final ReferenceIdProvider referenceIdProvider;
    private final ProcessorSupport processorSupport;

    ConcreteElementMetadataGenerator(ReferenceIdProvider referenceIdProvider, ProcessorSupport processorSupport)
    {
        this.referenceIdProvider = referenceIdProvider;
        this.processorSupport = processorSupport;
    }

    public ConcreteElementMetadata generateMetadata(CoreInstance concreteElement)
    {
        if (!PackageableElement.isPackageableElement(concreteElement, this.processorSupport))
        {
            throw new IllegalArgumentException("Not a PackageableElement: " + concreteElement);
        }

        String elementPath = PackageableElement.getUserPathForPackageableElement(concreteElement);

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

    private ConcreteElementMetadata.Builder computeExternalReferences(CoreInstance concreteElement, ConcreteElementMetadata.Builder builder)
    {
        MutableMap<String, MutableList<ResolvedGraphPath>> externalReferences = Maps.mutable.empty();
        GraphPathIterable.builder(this.processorSupport)
                .withStartNode(concreteElement)
                .withPropertyFilter((rgp, key) -> !M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.contains(rgp.getLastResolvedNode().getRealKeyByName(key)))
                .withPathFilter(rgp ->
                {
                    CoreInstance node = rgp.getLastResolvedNode();
                    boolean isExternal = isExternal(concreteElement, node);
                    return GraphWalkFilterResult.get(isExternal && !isFromM3Pure(node) && !Imports.isImportGroup(node, this.processorSupport), !isExternal);
                })
                .build()
                .forEach(rgp -> externalReferences.getIfAbsentPut(this.referenceIdProvider.getReferenceId(rgp.getLastResolvedNode()), Lists.mutable::empty).add(rgp));
        // Eliminate redundant graph paths
        externalReferences.forEachKeyValue((refId, refPaths) ->
        {
            if (refPaths.size() == 1)
            {
                // No need to look for redundant paths
                builder.withExternalReference(refId, refPaths.get(0).getGraphPath());
            }
            else
            {
                // Group paths by the last internal node and edge. If there are multiple paths to this point, they are
                // redundant: pick the best (shortest) one.
                MutableMap<Pair<CoreInstance, GraphPath.Edge>, MutableList<GraphPath>> byLastInternalNodeAndEdge = Maps.mutable.empty();
                refPaths.forEach(p ->
                {
                    ImmutableList<CoreInstance> nodes = p.getResolvedNodes();
                    CoreInstance lastInternalNode = nodes.get(nodes.size() - 2);
                    GraphPath.Edge lastEdge = p.getGraphPath().getEdge(p.getGraphPath().getEdgeCount() - 1);
                    byLastInternalNodeAndEdge.getIfAbsentPut(Tuples.pair(lastInternalNode, lastEdge), Lists.mutable::empty).add(p.getGraphPath());
                });
                byLastInternalNodeAndEdge.forEachValue(paths -> builder.withExternalReference(refId, paths.min(ConcreteElementMetadataGenerator::comparePaths)));
            }
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

    private boolean isFromM3Pure(CoreInstance node)
    {
        SourceInformation sourceInfo = node.getSourceInformation();
        return (sourceInfo != null) && "/platform/pure/grammar/m3.pure".equals(sourceInfo.getSourceId());
    }

    private static int comparePaths(GraphPath path1, GraphPath path2)
    {
        // NB: we don't need to compare start node paths, since we know they're all the same
        // First compare the number of edges: prefer shorter paths to longer
        // Then if we have the same number of edges, compare them individually
        int len = path1.getEdgeCount();
        int cmp = Integer.compare(len, path2.getEdgeCount());
        for (int i = 0; (cmp == 0) && (i < len); i++)
        {
            cmp = compareEdges(path1.getEdge(i), path2.getEdge(i));
        }
        return cmp;
    }

    private static int compareEdges(GraphPath.Edge edge1, GraphPath.Edge edge2)
    {
        int cmp = edge1.getProperty().compareTo(edge2.getProperty());
        return (cmp != 0) ? cmp : edge1.visit(new GraphPath.EdgeVisitor<Integer>()
        {
            @Override
            public Integer visit(GraphPath.ToOnePropertyEdge e1)
            {
                return (edge2 instanceof GraphPath.ToOnePropertyEdge) ? 0 : -1;
            }

            @Override
            public Integer visit(GraphPath.ToManyPropertyAtIndexEdge e1)
            {
                return edge2.visit(new GraphPath.EdgeVisitor<Integer>()
                {
                    @Override
                    public Integer visit(GraphPath.ToOnePropertyEdge e2)
                    {
                        return -1;
                    }

                    @Override
                    public Integer visit(GraphPath.ToManyPropertyAtIndexEdge e2)
                    {
                        return Integer.compare(e1.getIndex(), e2.getIndex());
                    }


                    @Override
                    public Integer visit(GraphPath.ToManyPropertyWithStringKeyEdge e2)
                    {
                        return 1;
                    }
                });
            }

            @Override
            public Integer visit(GraphPath.ToManyPropertyWithStringKeyEdge e1)
            {
                return edge2.visit(new GraphPath.EdgeVisitor<Integer>()
                {
                    @Override
                    public Integer visit(GraphPath.ToOnePropertyEdge e2)
                    {
                        return -1;
                    }

                    @Override
                    public Integer visit(GraphPath.ToManyPropertyAtIndexEdge e2)
                    {
                        return -1;
                    }

                    @Override
                    public Integer visit(GraphPath.ToManyPropertyWithStringKeyEdge e2)
                    {
                        int cmp = e1.getKeyProperty().compareTo(e2.getKeyProperty());
                        return (cmp != 0) ? cmp : e1.getKey().compareTo(e2.getKey());
                    }
                });
            }
        });
    }
}

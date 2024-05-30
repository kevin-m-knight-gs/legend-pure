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

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

class ExternalReferenceIndex
{
    private final MapIterable<String, ImmutableList<ExternalReference>> index;

    private ExternalReferenceIndex(MapIterable<String, ImmutableList<ExternalReference>> index)
    {
        this.index = index;
    }

    boolean hasReferencesTo(String referenceId)
    {
        return this.index.containsKey(referenceId);
    }

    RichIterable<String> getAllExternalReferenceIds()
    {
        return this.index.keysView();
    }

    RichIterable<ExternalReference> getAllExternalReferences()
    {
        return this.index.valuesView().flatCollect(exRefs -> exRefs);
    }

    ImmutableList<ExternalReference> getAllReferencesTo(String referenceId)
    {
        ImmutableList<ExternalReference> refs = this.index.get(referenceId);
        return (refs == null) ? Lists.immutable.empty() : refs;
    }

    void forEachExternalReference(Consumer<? super ExternalReference> consumer)
    {
        this.index.forEachValue(refs -> refs.forEach(consumer));
    }

    void forEachIdWithExternalReferences(BiConsumer<? super String, ? super ImmutableList<ExternalReference>> consumer)
    {
        this.index.forEachKeyValue(consumer::accept);
    }

    static ExternalReferenceIndex buildIndex(ElementIndex elementIndex)
    {
        MutableMap<String, MutableList<ExternalReference>> initialIndex = Maps.mutable.empty();
        elementIndex.forEachElement(e -> e.getExternalReferences().forEach(extRef -> initialIndex.getIfAbsentPut(extRef.getReferenceId(), Lists.mutable::empty).add(extRef)));

        MutableMap<String, ImmutableList<ExternalReference>> index = Maps.mutable.ofInitialCapacity(initialIndex.size());
        initialIndex.forEachKeyValue((refId, graphPaths) -> index.put(refId, graphPaths.sortThis((r1, r2) -> compareGraphPaths(r1.getPath(), r2.getPath())).toImmutable()));
        return new ExternalReferenceIndex(index);
    }

    private static int compareGraphPaths(GraphPath path1, GraphPath path2)
    {
        int cmp = path1.getStartNodePath().compareTo(path2.getStartNodePath());
        if (cmp != 0)
        {
            return cmp;
        }

        cmp = Integer.compare(path1.getEdgeCount(), path2.getEdgeCount());
        if (cmp != 0)
        {
            return cmp;
        }

        for (int i = 0, len = path1.getEdgeCount(); i < len; i++)
        {
            cmp = compareEdges(path1.getEdge(i), path2.getEdge(i));
            if (cmp != 0)
            {
                return cmp;
            }
        }
        return 0;
    }

    private static int compareEdges(GraphPath.Edge edge1, GraphPath.Edge edge2)
    {
        int cmp = edge1.getProperty().compareTo(edge2.getProperty());
        if (cmp != 0)
        {
            return cmp;
        }

        return edge1.visit(new GraphPath.EdgeVisitor<Integer>()
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
                        return 1;
                    }

                    @Override
                    public Integer visit(GraphPath.ToManyPropertyAtIndexEdge e2)
                    {
                        return Integer.compare(e1.getIndex(), e2.getIndex());
                    }

                    @Override
                    public Integer visit(GraphPath.ToManyPropertyWithStringKeyEdge e2)
                    {
                        return -1;
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
                        return 1;
                    }

                    @Override
                    public Integer visit(GraphPath.ToManyPropertyAtIndexEdge e2)
                    {
                        return 1;
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

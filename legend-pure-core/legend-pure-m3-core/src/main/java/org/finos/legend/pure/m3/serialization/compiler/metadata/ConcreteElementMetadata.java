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
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.utility.ArrayIterate;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.grammar.StringEscape;

import java.util.Objects;

public class ConcreteElementMetadata extends PackageableElementMetadata
{
    private final SourceInformation sourceInfo;
    private final int referenceIdVersion;
    private final ImmutableMap<String, ImmutableList<GraphPath>> externalReferences;

    private ConcreteElementMetadata(String path, String classifierPath, SourceInformation sourceInfo, int referenceIdVersion, ImmutableMap<String, ImmutableList<GraphPath>> externalReferences)
    {
        super(path, classifierPath);
        this.sourceInfo = Objects.requireNonNull(sourceInfo, "source information is required");
        if (!sourceInfo.isValid())
        {
            throw new IllegalArgumentException("Invalid source information for " + this.path);
        }
        this.referenceIdVersion = referenceIdVersion;
        this.externalReferences = externalReferences;
    }

    public SourceInformation getSourceInformation()
    {
        return this.sourceInfo;
    }

    public int getReferenceIdVersion()
    {
        return this.referenceIdVersion;
    }

    public ImmutableMap<String, ImmutableList<GraphPath>> getExternalReferences()
    {
        return this.externalReferences;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ConcreteElementMetadata))
        {
            return false;
        }

        ConcreteElementMetadata that = (ConcreteElementMetadata) other;
        return this.path.equals(that.path) &&
                this.classifierPath.equals(that.classifierPath) &&
                this.sourceInfo.equals(that.sourceInfo) &&
                (this.referenceIdVersion == that.referenceIdVersion) &&
                this.externalReferences.equals(that.externalReferences);
    }

    @Override
    public int hashCode()
    {
        int hashCode = this.path.hashCode();
        hashCode = 31 * hashCode + this.classifierPath.hashCode();
        hashCode = 31 * hashCode + this.sourceInfo.hashCode();
        hashCode = 31 * hashCode + this.referenceIdVersion;
        hashCode = 31 * hashCode + this.externalReferences.hashCode();
        return hashCode;
    }

    @Override
    protected void appendStringInfo(StringBuilder builder)
    {
        this.sourceInfo.appendMessage(builder.append(" sourceInfo="));
        builder.append(" referenceIdVersion=").append(this.referenceIdVersion);
        if (this.externalReferences.notEmpty())
        {
            builder.append(" externalReferences={");
            this.externalReferences.keyValuesView()
                    .toSortedListBy(Pair::getOne)
                    .forEach(pair ->
                    {
                        StringEscape.escape(builder.append('\''), pair.getOne()).append("'=");
                        ImmutableList<GraphPath> paths = pair.getTwo();
                        if (paths.size() == 1)
                        {
                            paths.get(0).writeDescription(builder);
                        }
                        else
                        {
                            builder.append('[');
                            paths.forEach(p -> p.writeDescription(builder).append(", "));
                            builder.setLength(builder.length() - 2);
                            builder.append(']');
                        }
                        builder.append(", ");
                    });
            builder.setLength(builder.length() - 2);
            builder.append('}');
        }
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(int initExtRefCapacity)
    {
        return new Builder(initExtRefCapacity);
    }

    public static class Builder
    {
        private String path;
        private String classifierPath;
        private SourceInformation sourceInfo;
        private Integer referenceIdVersion;
        private final MutableMap<String, MutableList<GraphPath>> externalReferences;

        private Builder()
        {
            this.externalReferences = Maps.mutable.empty();
        }

        private Builder(int initExtRefCapacity)
        {
            this.externalReferences = Maps.mutable.ofInitialCapacity(initExtRefCapacity);
        }

        public Builder withPath(String path)
        {
            this.path = path;
            return this;
        }

        public Builder withClassifierPath(String classifierPath)
        {
            this.classifierPath = classifierPath;
            return this;
        }

        public Builder withSourceInformation(SourceInformation sourceInfo)
        {
            this.sourceInfo = sourceInfo;
            return this;
        }

        public Builder withReferenceIdVersion(int referenceIdVersion)
        {
            this.referenceIdVersion = referenceIdVersion;
            return this;
        }

        public Builder withExternalReference(String referenceId, GraphPath path)
        {
            Objects.requireNonNull(referenceId);
            Objects.requireNonNull(path);
            this.externalReferences.getIfAbsentPut(referenceId, Lists.mutable::empty).add(path);
            return this;
        }

        public Builder withExternalReference(String referenceId, String path)
        {
            return withExternalReference(referenceId, GraphPath.parse(path));
        }

        public Builder withExternalReference(ExternalReference externalReference)
        {
            Objects.requireNonNull(externalReference);
            this.externalReferences.getIfAbsentPut(externalReference.getReferenceId(), Lists.mutable::empty).add(externalReference.getPath());
            return this;
        }

        public Builder withExternalReferences(String referenceId, GraphPath... paths)
        {
            Objects.requireNonNull(referenceId);
            if ((paths != null) && (paths.length > 0))
            {
                MutableList<GraphPath> list = this.externalReferences.getIfAbsentPut(referenceId, () -> Lists.mutable.ofInitialCapacity(paths.length));
                ArrayIterate.forEach(paths, p -> list.add(Objects.requireNonNull(p)));
            }
            return this;
        }

        public Builder withExternalReferences(String referenceId, String... paths)
        {
            Objects.requireNonNull(referenceId);
            if ((paths != null) && (paths.length > 0))
            {
                MutableList<GraphPath> list = this.externalReferences.getIfAbsentPut(referenceId, () -> Lists.mutable.ofInitialCapacity(paths.length));
                ArrayIterate.forEach(paths, p -> list.add(GraphPath.parse(p)));
            }
            return this;
        }

        public Builder withExternalReferences(ExternalReference... externalReferences)
        {
            ArrayIterate.forEach(externalReferences, this::withExternalReference);
            return this;
        }

        public ConcreteElementMetadata build()
        {
            Objects.requireNonNull(this.path, "path is required");
            Objects.requireNonNull(this.referenceIdVersion, "reference id version is required");
            return new ConcreteElementMetadata(this.path, this.classifierPath, this.sourceInfo, this.referenceIdVersion, processExternalReferences());
        }

        private ImmutableMap<String, ImmutableList<GraphPath>> processExternalReferences()
        {
            if (this.externalReferences.isEmpty())
            {
                return Maps.immutable.empty();
            }

            MutableMap<GraphPath, String> pathsToRefIds = Maps.mutable.ofInitialCapacity(this.externalReferences.size());
            MutableMap<String, ImmutableList<GraphPath>> result = Maps.mutable.ofInitialCapacity(this.externalReferences.size());
            this.externalReferences.forEachKeyValue((refId, refPaths) ->
            {
                if (refPaths.isEmpty())
                {
                    return;
                }

                // Sort paths and remove duplicates
                refPaths.sortThis(ConcreteElementMetadata::comparePaths);
                GraphPath[] prev = new GraphPath[1];
                refPaths.removeIf(current ->
                {
                    if (current.equals(prev[0]))
                    {
                        return true;
                    }
                    prev[0] = current;
                    return false;
                });

                // Validate that all graph paths start from this element and do not have other references
                refPaths.forEach(refPath ->
                {
                    if (!this.path.equals(refPath.getStartNodePath()))
                    {
                        StringBuilder builder = new StringBuilder("Invalid external reference for ").append(this.path).append(": ");
                        refPath.writeDescription(builder);
                        StringEscape.escape(builder.append("='"), refId).append("'");
                        throw new IllegalStateException(builder.toString());
                    }
                    String oldRefId;
                    if ((oldRefId = pathsToRefIds.put(refPath, refId)) != null)
                    {
                        StringBuilder builder = refPath.writeDescription(new StringBuilder("External reference conflict for "));
                        StringEscape.escape(builder.append(" between '"), oldRefId);
                        StringEscape.escape(builder.append("' and '"), refId).append("'");
                        throw new IllegalStateException(builder.toString());
                    }
                });

                // Put in the result
                result.put(refId, refPaths.toImmutable());
            });
            return result.toImmutable();
        }
    }

    private static int comparePaths(GraphPath path1, GraphPath path2)
    {
        // NB: we don't need to compare start node paths, since we know they're all the same
        // First compare the number of edges, since this is cheap
        int cmp = Integer.compare(path1.getEdgeCount(), path2.getEdgeCount());
        if (cmp != 0)
        {
            return cmp;
        }

        // If we have the same number of edges, compare them individually
        for (int i = 0, end = path1.getEdgeCount(); i < end; i++)
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

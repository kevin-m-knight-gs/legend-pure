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
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.Objects;

public class ConcreteElementMetadata extends PackageableElementMetadata
{
    private final SourceInformation sourceInfo;
    private final ImmutableList<ExternalReference> externalReferences;

    private ConcreteElementMetadata(String path, String classifierPath, SourceInformation sourceInfo, ImmutableList<ExternalReference> externalReferences)
    {
        super(path, classifierPath);
        this.sourceInfo = Objects.requireNonNull(sourceInfo, "source information is required");
        if (!sourceInfo.isValid())
        {
            throw new IllegalArgumentException("Invalid source information for " + this.path);
        }
        this.externalReferences = externalReferences;
    }

    public SourceInformation getSourceInformation()
    {
        return this.sourceInfo;
    }

    public ImmutableList<ExternalReference> getExternalReferences()
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
                this.externalReferences.equals(that.externalReferences);
    }

    @Override
    public int hashCode()
    {
        int hashCode = this.path.hashCode();
        hashCode = 31 * hashCode + this.classifierPath.hashCode();
        hashCode = 31 * hashCode + this.sourceInfo.hashCode();
        hashCode = 31 * hashCode + this.externalReferences.hashCode();
        return hashCode;
    }

    @Override
    protected void appendStringInfo(StringBuilder builder)
    {
        this.sourceInfo.appendMessage(builder.append(" sourceInfo="));
        if (this.externalReferences.notEmpty())
        {
            builder.append(" externalReferences=[");
            int len = builder.length();
            this.externalReferences.forEach(e -> e.appendMessage((builder.length() == len) ? builder : builder.append(", ")));
            builder.append(']');
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
        private final MutableList<ExternalReference> externalReferences;

        private Builder()
        {
            this.externalReferences = Lists.mutable.empty();
        }

        private Builder(int initExtRefCapacity)
        {
            this.externalReferences = Lists.mutable.ofInitialCapacity(initExtRefCapacity);
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

        public Builder withExternalReference(ExternalReference externalReference)
        {
            this.externalReferences.add(Objects.requireNonNull(externalReference));
            return this;
        }

        public Builder withExternalReferences(Iterable<? extends ExternalReference> externalReferences)
        {
            externalReferences.forEach(this::withExternalReference);
            return this;
        }

        public ConcreteElementMetadata build()
        {
            return new ConcreteElementMetadata(this.path, this.classifierPath, this.sourceInfo, processExternalReferences());
        }

        private ImmutableList<ExternalReference> processExternalReferences()
        {
            // Validate that all graph paths start from this element
            Objects.requireNonNull(this.path, "path is required");
            this.externalReferences.forEach(extRef ->
            {
                if (!this.path.equals(extRef.getPath().getStartNodePath()))
                {
                    throw new RuntimeException(extRef.appendMessage(new StringBuilder("Invalid external reference for ").append(this.path).append(": ")).toString());
                }
            });

            // Sort references and remove duplicates
            if (this.externalReferences.size() > 1)
            {
                this.externalReferences.sortThis(ConcreteElementMetadata::compareExternalReferences);
                ExternalReference[] prev = new ExternalReference[1];
                this.externalReferences.removeIf(current ->
                {
                    ExternalReference previous = prev[0];
                    if ((previous == null) || !previous.getPath().equals(current.getPath()))
                    {
                        prev[0] = current;
                        return false;
                    }
                    if (!previous.equals(current))
                    {
                        StringBuilder builder = previous.getPath().writeDescription(new StringBuilder("External reference conflict for "));
                        builder.append(" between ").append(previous.getReferenceId()).append(" and ").append(current.getReferenceId());
                        throw new RuntimeException(builder.toString());
                    }
                    return true;
                });
            }

            return this.externalReferences.toImmutable();
        }
    }

    private static int compareExternalReferences(ExternalReference extRef1, ExternalReference extRef2)
    {
        int cmp = comparePaths(extRef1.getPath(), extRef2.getPath());
        return (cmp != 0) ? cmp : extRef1.getReferenceId().compareTo(extRef2.getReferenceId());
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

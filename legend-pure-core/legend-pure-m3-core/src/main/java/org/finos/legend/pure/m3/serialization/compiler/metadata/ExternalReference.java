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

import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m4.serialization.grammar.StringEscape;

import java.util.Objects;

public class ExternalReference
{
    private final GraphPath path;
    private final String referenceId;

    public ExternalReference(GraphPath path, String referenceId)
    {
        this.path = validateGraphPath(path);
        this.referenceId = validateReferenceId(referenceId);
    }

    public GraphPath getPath()
    {
        return this.path;
    }

    public String getReferenceId()
    {
        return this.referenceId;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }

        if (!(other instanceof ExternalReference))
        {
            return false;
        }

        ExternalReference that = (ExternalReference) other;
        return this.referenceId.equals(that.referenceId) && this.path.equals(that.path);
    }

    @Override
    public int hashCode()
    {
        return this.path.hashCode() + (31 * this.referenceId.hashCode());
    }

    @Override
    public String toString()
    {
        return appendMessage(new StringBuilder("<").append(getClass().getSimpleName()).append(' ')).append('>').toString();
    }

    StringBuilder appendMessage(StringBuilder builder)
    {
        return StringEscape.escape(this.path.writeDescription(builder).append("='"), this.referenceId).append('\'');
    }

    private static GraphPath validateGraphPath(GraphPath path)
    {
        Objects.requireNonNull(path, "path is required");
        if (path.getEdgeCount() == 0)
        {
            throw new IllegalArgumentException("external reference graph path must have at least one edge");
        }
        return path;
    }

    private static String validateReferenceId(String referenceId)
    {
        Objects.requireNonNull(referenceId, "reference id is required");
        if (referenceId.isEmpty())
        {
            throw new IllegalArgumentException("external reference id may not be empty");
        }
        return referenceId;
    }
}

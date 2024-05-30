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
import org.junit.Assert;
import org.junit.Test;

public class TestExternalReference
{
    @Test
    public void testPathIsRequired()
    {
        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> new ExternalReference(null, "abc"));
        Assert.assertEquals("path is required", e.getMessage());
    }

    @Test
    public void testInvalidPath()
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> new ExternalReference(GraphPath.parse("model::test::MyClass"), "abc"));
        Assert.assertEquals("external reference graph path must have at least one edge", e.getMessage());
    }

    @Test
    public void testReferenceIdIsRequired()
    {
        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> new ExternalReference(GraphPath.parse("model::test::MyClass.prop1"), null));
        Assert.assertEquals("reference id is required", e.getMessage());
    }

    @Test
    public void testReferenceIdIsNonEmpty()
    {
        IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class, () -> new ExternalReference(GraphPath.parse("model::test::MyClass.prop1"), ""));
        Assert.assertEquals("external reference id may not be empty", e.getMessage());
    }
}

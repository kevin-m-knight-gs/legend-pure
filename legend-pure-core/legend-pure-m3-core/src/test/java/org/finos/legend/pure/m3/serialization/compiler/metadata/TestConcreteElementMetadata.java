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

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.junit.Assert;
import org.junit.Test;

public class TestConcreteElementMetadata
{
    @Test
    public void testPathRequired()
    {
        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> ConcreteElementMetadata.builder()
                .withClassifierPath(M3Paths.Class)
                .withSourceInformation(new SourceInformation("/source.pure", 1, 1, 2, 2, 3, 3))
                .build());
        Assert.assertEquals("path is required", e.getMessage());
    }

    @Test
    public void testClassifierPathRequired()
    {
        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> ConcreteElementMetadata.builder()
                .withPath("model::test::MyClass")
                .withSourceInformation(new SourceInformation("/source.pure", 1, 1, 2, 2, 3, 3))
                .build());
        Assert.assertEquals("classifier path is required", e.getMessage());
    }

    @Test
    public void testSourceInfoRequired()
    {
        NullPointerException e = Assert.assertThrows(NullPointerException.class, () -> ConcreteElementMetadata.builder()
                .withPath("model::test::MyClass")
                .withClassifierPath(M3Paths.Class)
                .build());
        Assert.assertEquals("source information is required", e.getMessage());
    }

    @Test
    public void testInvalidSourceInfo()
    {
        MutableList<SourceInformation> invalidSourceInfo = Lists.mutable.with(
                new SourceInformation(null, 1, 1, 2, 2, 3, 3),
                new SourceInformation("/source.pure", 0, 1, 2, 2, 3, 3),
                new SourceInformation("/source.pure", 1, 0, 2, 2, 3, 3),
                new SourceInformation("/source.pure", 2, 2, 1, 3, 4, 4),
                new SourceInformation("/source.pure", 2, 2, 2, 1, 4, 4),
                new SourceInformation("/source.pure", 2, 2, 3, 3, 3, 2)
        );
        for (SourceInformation sourceInfo : invalidSourceInfo)
        {
            String message = sourceInfo.toString();
            IllegalArgumentException e = Assert.assertThrows(message, IllegalArgumentException.class, () -> ConcreteElementMetadata.builder()
                    .withPath("model::test::MyClass")
                    .withClassifierPath(M3Paths.Class)
                    .withSourceInformation(sourceInfo)
                    .build());
            Assert.assertEquals(message, "Invalid source information for model::test::MyClass", e.getMessage());
        }
    }
}

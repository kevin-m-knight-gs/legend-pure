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

package org.finos.legend.pure.m3.serialization.compiler.reference;

import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
import org.finos.legend.pure.m4.tools.GraphWalkFilterResult;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class AbstractReferenceIdResolverTest extends AbstractReferenceTest
{
    protected static ReferenceIdProvider referenceIdProvider;
    protected static ReferenceIdResolver referenceIdResolver;

    @BeforeClass
    public static void setUpReferenceIdProvider()
    {
        referenceIdProvider = ReferenceIdProviders.fromProcessorSupport(processorSupport, true);
    }

    @Test
    public void testAllInstancesWithReferenceIds()
    {
        GraphNodeIterable.builder()
                .withStartingNodes(repository.getTopLevels())
                .withKeyFilter((node, key) -> !M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.contains(node.getRealKeyByName(key)))
                .withNodeFilter(node -> GraphWalkFilterResult.get(!AnyStubHelper.isStub(node) && ((node.getSourceInformation() != null) || _Package.isPackage(node, processorSupport)), true))
                .build()
                .forEach(instance ->
                {
                    String id = referenceIdProvider.getReferenceId(instance);
                    CoreInstance resolved = referenceIdResolver.resolveReference(id);
                    Assert.assertSame(id, instance, resolved);
                });
    }
}

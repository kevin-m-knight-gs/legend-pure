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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
import org.finos.legend.pure.m4.tools.GraphWalkFilterResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.ServiceLoader;

public abstract class AbstractReferenceIdExtensionTest extends AbstractReferenceTest
{
    protected static ReferenceIdExtension extension;

    @Test
    public void testLoadAsService()
    {
        int version = extension.version();
        MutableList<ReferenceIdExtension> extensionsAtVersion = Iterate.select(ServiceLoader.load(ReferenceIdExtension.class), ext -> version == ext.version(), Lists.mutable.empty());
        Assert.assertEquals(Lists.fixedSize.with(extension.getClass()), extensionsAtVersion.collect(Object::getClass));
    }

    @Test
    public void testReferenceIds()
    {
        int version = extension.version();
        ReferenceIds referenceIds = ReferenceIds.builder(processorSupport).withExtension(extension).build();
        Assert.assertTrue(referenceIds.isVersionAvailable(version));
        Assert.assertSame(extension, referenceIds.getExtension(version));
    }

    @Test
    public void testProviderAndResolverVersions()
    {
        Assert.assertEquals(extension.version(), extension.newProvider(processorSupport).version());
        Assert.assertEquals(extension.version(), extension.newResolver(processorSupport).version());
    }

    @Test
    public void testAllInstancesWithReferenceIds()
    {
        ReferenceIdProvider provider = extension.newProvider(processorSupport);
        ReferenceIdResolver resolver = extension.newResolver(processorSupport);
        GraphNodeIterable.builder()
                .withStartingNodes(repository.getTopLevels())
                .withKeyFilter((node, key) -> !M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.contains(node.getRealKeyByName(key)))
                .withNodeFilter(node -> GraphWalkFilterResult.get(!AnyStubHelper.isStub(node) && ((node.getSourceInformation() != null) || _Package.isPackage(node, processorSupport)), true))
                .build()
                .forEach(instance ->
                {
                    String id = provider.getReferenceId(instance);
                    CoreInstance resolved = resolver.resolveReference(id);
                    Assert.assertSame(id, instance, resolved);
                });
    }
}
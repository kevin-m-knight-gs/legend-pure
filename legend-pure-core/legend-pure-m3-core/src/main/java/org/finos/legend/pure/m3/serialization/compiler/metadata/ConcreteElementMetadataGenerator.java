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

import org.eclipse.collections.api.LazyIterable;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProvider;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.tools.GraphNodeIterable;
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

        return ConcreteElementMetadata.builder()
                .withPath(elementPath)
                .withClassifierPath(PackageableElement.getUserPathForPackageableElement(classifier))
                .withSourceInformation(sourceInfo)
                .withExternalReferences(getExternalReferences(concreteElement))
                .build();
    }

    private LazyIterable<String> getExternalReferences(CoreInstance concreteElement)
    {
        return GraphNodeIterable.builder()
                .withStartingNode(concreteElement)
                .withKeyFilter((node, key) -> !M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.contains(node.getRealKeyByName(key)))
                .withNodeFilter(node -> isExternal(concreteElement, node) ? GraphWalkFilterResult.ACCEPT_AND_STOP : GraphWalkFilterResult.REJECT_AND_CONTINUE)
                .build()
                .collect(this.referenceIdProvider::getReferenceId);
    }

    private boolean isExternal(CoreInstance concreteElement, CoreInstance node)
    {
        if (concreteElement == node)
        {
            return false;
        }

        SourceInformation nodeSourceInfo = node.getSourceInformation();
        return (nodeSourceInfo == null) ? _Package.isPackage(node, this.processorSupport) : !concreteElement.getSourceInformation().subsumes(nodeSourceInfo);
    }
}

// Copyright 2025 Goldman Sachs
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

package org.finos.legend.pure.m3.coreinstance.lazy;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.serialization.compiler.metadata.MetadataIndex;
import org.finos.legend.pure.m3.serialization.compiler.metadata.PackageableElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolvers;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;

import java.util.function.Function;

public abstract class AbstractLazyPackageableElement extends AbstractLazyCoreInstance implements PackageableElement
{
    protected final String path;
    protected final OneValue<org.finos.legend.pure.m3.coreinstance.Package> _package;

    protected AbstractLazyPackageableElement(ModelRepository repository, int internalSyntheticId, String path, SourceInformation sourceInformation, int compileStateBitSet, String classifierPath, Function<? super String, ? extends CoreInstance> packagePathResolver)
    {
        super(repository, internalSyntheticId, getNameFromPath(path), sourceInformation, compileStateBitSet, classifierPath, packagePathResolver);
        this.path = path;
        String pkg = getPackageFromPath(path);
        this._package = (pkg == null) ? fromValue(null) : fromSupplier(packageableElementSupplier(packagePathResolver, pkg));
    }

    protected AbstractLazyPackageableElement(ModelRepository repository, int internalSyntheticId, SourceInformation sourceInformation, int compileStateBitSet, PackageableElementMetadata metadata, Function<? super String, ? extends CoreInstance> packagePathResolver)
    {
        this(repository, internalSyntheticId, metadata.getPath(), sourceInformation, compileStateBitSet, metadata.getClassifierPath(), packagePathResolver);
    }

    protected AbstractLazyPackageableElement(ModelRepository repository, int internalSyntheticId, SourceInformation sourceInformation, int compileStateBitSet, PackageableElementMetadata metadata, ReferenceIdResolvers referenceIds)
    {
        this(repository, internalSyntheticId, metadata.getPath(), sourceInformation, compileStateBitSet, metadata.getClassifierPath(), referenceIds.packagePathResolver());
    }

    protected AbstractLazyPackageableElement(AbstractLazyPackageableElement source)
    {
        super(source);
        this.path = source.path;
        this._package = source._package.copy();
    }

    @Override
    public String _name()
    {
        return this.name;
    }

    @Override
    public PackageableElement _name(String name)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PackageableElement _nameRemove()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public org.finos.legend.pure.m3.coreinstance.Package _package()
    {
        return this._package.getValue();
    }

    @Override
    public PackageableElement _package(Package value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public PackageableElement _packageRemove()
    {
        throw new UnsupportedOperationException();
    }

    // Package children

    protected static String getNameFromPath(String path)
    {
        int lastColon = path.lastIndexOf(':');
        return (lastColon == -1) ? path : path.substring(lastColon + 1);
    }

    protected static String getPackageFromPath(String path)
    {
        int lastColon = path.lastIndexOf(':');
        return (lastColon == -1) ?
               (GraphTools.isTopLevelName(path) ? null : M3Paths.Root) :
               path.substring(0, lastColon - 1);
    }

    protected static ManyValues<PackageableElement> computePackageChildren(String path, MetadataIndex metadataIndex, ReferenceIdResolvers referenceIds)
    {
        return computePackageChildren(path, metadataIndex, referenceIds.packagePathResolver());
    }

    protected static ManyValues<PackageableElement> computePackageChildren(String path, MetadataIndex metadataIndex, Function<? super String, ? extends CoreInstance> packagePathResolver)
    {
        ImmutableList<PackageableElementMetadata> children = metadataIndex.getPackageChildren(path);
        return children.isEmpty() ?
               fromValues(null) :
               fromSuppliers(children.collect(child -> packageableElementSupplier(packagePathResolver, child.getPath()), Lists.mutable.ofInitialCapacity(children.size())));
    }
}

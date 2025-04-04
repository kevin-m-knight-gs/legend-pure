// Copyright 2020 Goldman Sachs
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

package org.finos.legend.pure.m2.dsl.mapping.serialization.grammar.v1.processor;

import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.impl.factory.Lists;
import org.finos.legend.pure.m2.dsl.mapping.M2MappingPaths;
import org.finos.legend.pure.m3.compiler.Context;
import org.finos.legend.pure.m3.compiler.postprocessing.PostProcessor;
import org.finos.legend.pure.m3.compiler.postprocessing.ProcessorState;
import org.finos.legend.pure.m3.compiler.postprocessing.SpecializationProcessor;
import org.finos.legend.pure.m3.compiler.postprocessing.processor.Processor;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.InstanceSetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.Mapping;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.MappingClass;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.MappingClassCoreInstanceWrapper;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.PropertyMapping;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.PropertyMappingsImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.mapping.SetImplementation;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PropertyOwner;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Generalization;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Type;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.GenericType;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.navigation.importstub.ImportStub;
import org.finos.legend.pure.m3.tools.ListHelper;
import org.finos.legend.pure.m3.tools.matcher.Matcher;
import org.finos.legend.pure.m4.ModelRepository;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.exception.PureCompilationException;

public class SetImplementationProcessor extends Processor<SetImplementation>
{
    @Override
    public String getClassName()
    {
        return M2MappingPaths.SetImplementation;
    }

    @Override
    public void process(SetImplementation classMapping, ProcessorState state, Matcher matcher, ModelRepository repository, Context context, ProcessorSupport processorSupport)
    {
        PropertyOwner _class = (PropertyOwner) ImportStub.withImportStubByPass(classMapping._classCoreInstance(), processorSupport);
        if (_class == null)
        {
            throw new PureCompilationException(classMapping.getSourceInformation(), "Class mapping missing class");
        }
        PostProcessor.processElement(matcher, _class, state, processorSupport);

        ensureSetImplementationHasId(classMapping, repository, processorSupport);

        if (classMapping instanceof InstanceSetImplementation)
        {
            ((InstanceSetImplementation) classMapping)._propertyMappings().forEach(pm -> PropertyMappingProcessor.processPropertyMapping(pm, repository, processorSupport, _class, (PropertyMappingsImplementation) classMapping));
        }

        this.buildMappingClassOutOfLocalProperties(repository, classMapping, state, matcher, processorSupport);
    }

    private void buildMappingClassOutOfLocalProperties(ModelRepository repository, SetImplementation classMapping, ProcessorState state, Matcher matcher, ProcessorSupport processorSupport)
    {
        ListIterable<? extends PropertyMapping> localProperties = classMapping instanceof InstanceSetImplementation ? ((InstanceSetImplementation) classMapping)._propertyMappings().select(propertyMapping -> (propertyMapping._localMappingProperty() != null) && propertyMapping._localMappingProperty(), Lists.mutable.empty()) : Lists.immutable.empty();

        CoreInstance _class = ImportStub.withImportStubByPass(classMapping._classCoreInstance(), processorSupport);

        boolean hasAggregateSpecification = classMapping instanceof InstanceSetImplementation && ((InstanceSetImplementation) classMapping)._aggregateSpecification() != null;

        if (localProperties.notEmpty() || hasAggregateSpecification)
        {
            Mapping mapping = (Mapping) ImportStub.withImportStubByPass(classMapping._parentCoreInstance(), processorSupport);
            SourceInformation sourceInfo = classMapping.getSourceInformation();
            MappingClass newClass = MappingClassCoreInstanceWrapper.toMappingClass(repository.newCoreInstance(_class.getName() + "_" + mapping.getName() + "_" + classMapping._id(), _Package.getByUserPath(M2MappingPaths.MappingClass, processorSupport), sourceInfo));

            newClass._name(_class.getName() + "_" + mapping.getName() + "_" + classMapping._id());
            CoreInstance genericTypeClass = _Package.getByUserPath(M3Paths.GenericType, processorSupport);
            GenericType genericType = (GenericType) repository.newAnonymousCoreInstance(sourceInfo, genericTypeClass);
            genericType._rawType((Type) _Package.getByUserPath(M2MappingPaths.MappingClass, processorSupport));
            GenericType typeArg = (GenericType) repository.newAnonymousCoreInstance(sourceInfo, genericTypeClass);
            typeArg._rawType(newClass);
            genericType._typeArgumentsAdd(typeArg);
            newClass._classifierGenericType(genericType);

            GenericType superType = (GenericType) repository.newAnonymousCoreInstance(sourceInfo, genericTypeClass);
            superType._rawTypeCoreInstance(_class);
            Generalization newGeneralization = (Generalization) repository.newAnonymousCoreInstance(sourceInfo, _Package.getByUserPath(M3Paths.Generalization, processorSupport));
            newGeneralization._specific(newClass);
            newGeneralization._general(superType);
            newClass._generalizations(Lists.immutable.with(newGeneralization));

            newClass._propertiesAddAll(localProperties.collect(propertyMapping ->
            {
                Property property = (Property) ImportStub.withImportStubByPass(propertyMapping._propertyCoreInstance(), processorSupport);
                property._owner(newClass);
                GenericType src = ListHelper.wrapListIterable(property._classifierGenericType()._typeArguments()).get(0);
                src._rawType(newClass);
                return property;
            }));

            SpecializationProcessor.process(newClass, processorSupport);
            PostProcessor.processElement(matcher, newClass, state, processorSupport);

            ((InstanceSetImplementation) classMapping)._mappingClass(newClass);
        }
    }

    @Override
    public void populateReferenceUsages(SetImplementation classMapping, ModelRepository repository, ProcessorSupport processorSupport)
    {
        addReferenceUsageForToOneProperty(classMapping, classMapping._classCoreInstance(), M3Properties._class, repository, processorSupport);
        for (PropertyMapping propertyMapping : classMapping instanceof InstanceSetImplementation ? ((InstanceSetImplementation) classMapping)._propertyMappings() : Lists.immutable.<PropertyMapping>empty())
        {
            PropertyMappingProcessor.populateReferenceUsagesForPropertyMapping(propertyMapping, repository, processorSupport);
        }
    }

    public static void ensureSetImplementationHasId(SetImplementation setImplementation, ModelRepository repository, ProcessorSupport processorSupport)
    {
        if (setImplementation._id() == null)
        {
            PropertyOwner _class = (PropertyOwner) ImportStub.withImportStubByPass(setImplementation._classCoreInstance(), processorSupport);
            if (_class == null)
            {
                throw new PureCompilationException(setImplementation.getSourceInformation(), "SetImplementation missing class");
            }

            String id = PackageableElement.getUserPathForPackageableElement(_class, "_");
            setImplementation._id(id);
        }
    }
}

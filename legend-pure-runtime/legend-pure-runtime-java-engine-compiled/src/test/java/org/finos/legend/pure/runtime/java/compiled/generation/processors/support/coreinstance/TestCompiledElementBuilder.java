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

package org.finos.legend.pure.runtime.java.compiled.generation.processors.support.coreinstance;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.finos.legend.pure.m3.coreinstance.Package;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.ModelElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.ReferenceUsage;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.constraint.Constraint;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.ElementWithConstraints;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Stereotype;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.Tag;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.extension.TaggedValue;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.ConcreteFunctionDefinition;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.FunctionDefinition;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Generalization;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enum;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Enumeration;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.FunctionType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Measure;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.PrimitiveType;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Type;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Unit;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.valuespecification.VariableExpression;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.generictype.GenericType;
import org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity;
import org.finos.legend.pure.m3.serialization.compiler.PureCompilerSerializer;
import org.finos.legend.pure.m3.serialization.compiler.element.ConcreteElementSerializer;
import org.finos.legend.pure.m3.serialization.compiler.element.DeserializedConcreteElement;
import org.finos.legend.pure.m3.serialization.compiler.element.ElementBuilder;
import org.finos.legend.pure.m3.serialization.compiler.element.ElementLoader;
import org.finos.legend.pure.m3.serialization.compiler.element.InstanceData;
import org.finos.legend.pure.m3.serialization.compiler.file.FileDeserializer;
import org.finos.legend.pure.m3.serialization.compiler.file.FilePathProvider;
import org.finos.legend.pure.m3.serialization.compiler.file.FileSerializer;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ConcreteElementMetadata;
import org.finos.legend.pure.m3.serialization.compiler.metadata.MetadataIndex;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataGenerator;
import org.finos.legend.pure.m3.serialization.compiler.metadata.ModuleMetadataSerializer;
import org.finos.legend.pure.m3.serialization.compiler.metadata.VirtualPackageMetadata;
import org.finos.legend.pure.m3.serialization.compiler.reference.AbstractReferenceTest;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdProviders;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolver;
import org.finos.legend.pure.m3.serialization.compiler.reference.ReferenceIdResolvers;
import org.finos.legend.pure.m3.tools.GraphTools;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.runtime.java.compiled.generation.JavaPackageAndImportBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class TestCompiledElementBuilder extends AbstractReferenceTest
{
    @ClassRule
    public static TemporaryFolder TMP = new TemporaryFolder();

    private static Path serializationDir;
    private static FileDeserializer fileDeserializer;
    private static MetadataIndex metadataIndex;

    private ElementBuilderWrapper elementBuilder;
    private ElementLoader elementLoader;

    @BeforeClass
    public static void serialize() throws IOException
    {
        serializationDir = TMP.newFolder("files").toPath();

        ReferenceIdProviders referenceIds = ReferenceIdProviders.builder().withProcessorSupport(processorSupport).withAvailableExtensions().build();
        FileSerializer fileSerializer = FileSerializer.builder()
                .withFilePathProvider(FilePathProvider.builder().withLoadedExtensions().build())
                .withSerializers(ConcreteElementSerializer.builder(processorSupport).withLoadedExtensions().withReferenceIdProviders(referenceIds).build(), ModuleMetadataSerializer.builder().withLoadedExtensions().build())
                .build();
        fileDeserializer = fileSerializer.getDeserializer();

        PureCompilerSerializer.builder()
                .withFileSerializer(fileSerializer)
                .withModuleMetadataGenerator(ModuleMetadataGenerator.fromProcessorSupport(processorSupport))
                .withProcessorSupport(processorSupport)
                .build()
                .serializeAll(serializationDir);

        metadataIndex = MetadataIndex.builder()
                .withModules(runtime.getCodeStorage().getAllRepositories().collect(r -> fileDeserializer.deserializeModuleMetadata(serializationDir, r.getName())))
                .build();
    }

    @Before
    public void setUpElementBuilderAndLoader()
    {
        this.elementBuilder = new ElementBuilderWrapper(CompiledElementBuilder.newElementBuilder(Thread.currentThread().getContextClassLoader(), new CompiledPrimitiveValueResolver()));
        this.elementLoader = ElementLoader.builder()
                .withMetadataIndex(metadataIndex)
                .withElementBuilder(this.elementBuilder)
                .withAvailableReferenceIdExtensions()
                .withFileDeserializer(fileDeserializer)
                .withDirectory(serializationDir)
                .build();
    }

    @Test
    public void testAllTopLevelAndPackagedElements()
    {
        GraphTools.getTopLevelAndPackagedElements(processorSupport).forEach(element ->
        {
            String path = org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.getUserPathForPackageableElement(element);
            if (element.getSourceInformation() == null)
            {
                assertLoadVirtualPackage(path, false);
            }
            else
            {
                String classifierPath = org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.getUserPathForPackageableElement(element.getClassifier());
                assertLoadConcreteElement(path, classifierPath, false);
            }
        });
    }

    @Test
    public void testBuildRootPackage()
    {
        testConcretePackage(M3Paths.Root);
    }

    @Test
    public void testBuildMetaPackage()
    {
        testConcretePackage("meta");
    }

    @Test
    public void testBuildMetaPurePackage()
    {
        testConcretePackage("meta::pure");
    }

    @Test
    public void testBuildTestPackage()
    {
        testVirtualPackage("test");
    }

    @Test
    public void testBuildTestModelPackage()
    {
        testVirtualPackage("test::model");
    }

    private void testConcretePackage(String path)
    {
        Package metaPure = assertLoadConcreteElement(path, M3Paths.Package);
        testElement(path, metaPure);
    }

    private void testVirtualPackage(String path)
    {
        Package test = assertLoadVirtualPackage(path);
        testElement(path, test);
    }

    @Test
    public void testBuildClassClass()
    {
        testClass(M3Paths.Class);
    }

    @Test
    public void testBuildAssociationClass()
    {
        testClass(M3Paths.Association);
    }

    @Test
    public void testBuildPackageClass()
    {
        testClass(M3Paths.Package);
    }

    @Test
    public void testBuildPrimitiveTypeClass()
    {
        testClass(M3Paths.PrimitiveType);
    }

    @Test
    public void testBuildSimpleClass()
    {
        testClass("test::model::SimpleClass");
    }

    @Test
    public void testBuildClassWithQualifiedProperties()
    {
        testClass("test::model::ClassWithQualifiedProperties");
    }

    @Test
    public void testBuildClassWithTypeVariables()
    {
        testClass("test::model::ClassWithTypeVariables");
    }

    private void testClass(String path)
    {
        Class<?> cls = assertLoadConcreteElement(path, M3Paths.Class);
        testElement(path, cls);
    }

    @Test
    public void testBuildInteger()
    {
        testPrimitiveType(M3Paths.Integer);
    }

    @Test
    public void testBuildNumber()
    {
        testPrimitiveType(M3Paths.Number);
    }

    @Test
    public void testBuildString()
    {
        testPrimitiveType(M3Paths.String);
    }

    @Test
    public void testBuildRangedInt()
    {
        testPrimitiveType("test::model::RangedInt");
    }

    private void testPrimitiveType(String path)
    {
        PrimitiveType primitiveType = assertLoadConcreteElement(path, M3Paths.PrimitiveType);
        testElement(path, primitiveType);
    }

    @Test
    public void testBuildAggregationKind()
    {
        testEnumeration(M3Paths.AggregationKind);
    }

    @Test
    public void testBuildSimpleEnumeration()
    {
        testEnumeration("test::model::SimpleEnumeration");
    }

    private void testEnumeration(String path)
    {
        Enumeration<? extends Enum> enumeration = assertLoadConcreteElement(path, M3Paths.Enumeration);
        testElement(path, enumeration);
    }

    @Test
    public void testBuildLeftRightAssociation()
    {
        testAssociation("test::model::LeftRight");
    }

    @Test
    public void testBuildAssociationWithMilestoning1()
    {
        testAssociation("test::model::AssociationWithMilestoning1");
    }

    @Test
    public void testBuildAssociationWithMilestoning2()
    {
        testAssociation("test::model::AssociationWithMilestoning2");
    }

    @Test
    public void testBuildAssociationWithMilestoning3()
    {
        testAssociation("test::model::AssociationWithMilestoning3");
    }

    private void testAssociation(String path)
    {
        Association association = assertLoadConcreteElement(path, M3Paths.Association);
        testElement(path, association);
    }

    @Test
    public void testBuildMass()
    {
        testMeasure("test::model::Mass");
    }

    @Test
    public void testBuildCurrency()
    {
        testMeasure("test::model::Currency");
    }

    private void testMeasure(String path)
    {
        Measure measure = assertLoadConcreteElement(path, M3Paths.Measure);
        testElement(path, measure);
    }

    @Test
    public void testBuildTestFunc()
    {
        testConcreteFunctionDefinition("test::model::testFunc_T_m__Function_$0_1$__String_m_");
    }

    @Test
    public void testBuildTestFunc2()
    {
        testConcreteFunctionDefinition("test::model::testFunc2__String_1_");
    }

    @Test
    public void testBuildTestFunc3()
    {
        testConcreteFunctionDefinition("test::model::testFunc3__Any_MANY_");
    }

    @Test
    public void testBuildTestFunc4()
    {
        testConcreteFunctionDefinition("test::model::testFunc4_ClassWithMilestoning1_1__ClassWithMilestoning3_MANY_");
    }

    private void testConcreteFunctionDefinition(String path)
    {
        ConcreteFunctionDefinition<?> func = assertLoadConcreteElement(path, M3Paths.ConcreteFunctionDefinition);
        testElement(path, func);
    }

    private Package assertLoadVirtualPackage(String path)
    {
        return assertLoadVirtualPackage(path, true);
    }

    private Package assertLoadVirtualPackage(String path, boolean requireNotLoaded)
    {
        if (requireNotLoaded)
        {
            Assert.assertFalse(path, this.elementBuilder.virtualPackages.contains(path));
        }
        PackageableElement element = this.elementLoader.loadElement(path);
        Assert.assertNotNull(path, element);
        Assert.assertTrue(path, this.elementBuilder.virtualPackages.contains(path));
        String expectedJavaClassName = JavaPackageAndImportBuilder.buildLazyVirtualPackageClassReference();
        Assert.assertEquals(path, expectedJavaClassName, element.getClass().getName());
        assertPackageableElement(path, M3Paths.Package, element);
        Assert.assertTrue(path, element instanceof Package);
        return (Package) element;
    }

    private <T extends PackageableElement> T assertLoadConcreteElement(String path, String classifierPath)
    {
        return assertLoadConcreteElement(path, classifierPath, true);
    }

    @SuppressWarnings("unchecked")
    private <T extends PackageableElement> T assertLoadConcreteElement(String path, String classifierPath, boolean requireNotLoaded)
    {
        if (requireNotLoaded)
        {
            Assert.assertFalse(path, this.elementBuilder.concreteElements.contains(path));
        }
        PackageableElement element = this.elementLoader.loadElement(path);
        Assert.assertNotNull(path, element);
        Assert.assertTrue(path, this.elementBuilder.concreteElements.contains(path));
        String expectedJavaClassName = JavaPackageAndImportBuilder.buildLazyConcreteElementClassReferenceFromUserPath(classifierPath);
        Assert.assertEquals(path, expectedJavaClassName, element.getClass().getName());
        assertPackageableElement(path, classifierPath, element);
        return (T) element;
    }

    private void assertPackageableElement(String path, String classifierPath, PackageableElement element)
    {
        // This asserts things about a PackageableElement which can be accessed without deserializing the element

        // full system path
        Assert.assertEquals(path, GraphTools.isTopLevelName(classifierPath) ? classifierPath : ("Root::" + classifierPath), element.getFullSystemPath());

        // source element
        CoreInstance srcElement = runtime.getCoreInstance(path);
        Assert.assertNotNull(path, srcElement);

        // name and package
        int lastColon = path.lastIndexOf(':');
        if (lastColon == -1)
        {
            Assert.assertEquals(path, element._name());
            Assert.assertEquals(path, element.getName());
            if (GraphTools.isTopLevelName(path))
            {
                Assert.assertNull(path, element._package());
            }
            else
            {
                Package pkg = element._package();
                Assert.assertEquals(JavaPackageAndImportBuilder.buildLazyConcreteElementClassReferenceFromUserPath(M3Paths.Package), pkg.getClass().getName());
                Assert.assertEquals(path, M3Paths.Root, getUserPath(pkg));
                Assert.assertSame(path, this.elementLoader.loadElement(M3Paths.Root), pkg);
                Assert.assertTrue(path, this.elementBuilder.concreteElements.contains(M3Paths.Root));
            }
        }
        else
        {
            String name = path.substring(lastColon + 1);
            String packagePath = path.substring(0, lastColon - 1);
            Assert.assertEquals(path, name, element._name());
            Assert.assertEquals(path, name, element.getName());
            Package pkg = element._package();
            Assert.assertEquals(path, packagePath, getUserPath(pkg));
            if (this.elementBuilder.concreteElements.contains(packagePath))
            {
                Assert.assertEquals(JavaPackageAndImportBuilder.buildLazyConcreteElementClassReferenceFromUserPath(M3Paths.Package), pkg.getClass().getName());
            }
            else if (this.elementBuilder.virtualPackages.contains(packagePath))
            {
                Assert.assertEquals(JavaPackageAndImportBuilder.buildLazyVirtualPackageClassReference(), pkg.getClass().getName());
            }
            else
            {
                Assert.fail("Package for " + path + " not loaded");
            }
            Assert.assertSame(path, this.elementLoader.loadElement(packagePath), pkg);
        }

        // source information
        Assert.assertEquals(path, srcElement.getSourceInformation(), element.getSourceInformation());

        // classifier
        CoreInstance classifier = element.getClassifier();
        Assert.assertEquals(path, classifierPath, getUserPath(classifier));
        Assert.assertTrue(path, this.elementBuilder.concreteElements.contains(classifierPath));
        Assert.assertSame(this.elementLoader.loadElement(classifierPath), classifier);

        // Java class
        Assert.assertEquals(JavaPackageAndImportBuilder.buildLazyConcreteElementClassReferenceFromUserPath(M3Paths.Class), classifier.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private void testElement(String path, PackageableElement element)
    {
        PackageableElement srcElement = (PackageableElement) runtime.getCoreInstance(path);
        Assert.assertNotNull(path, srcElement);
        Assert.assertNotSame(path, srcElement, element);

        Assert.assertEquals(path, srcElement._name(), element._name());
        Assert.assertEquals(path, srcElement.getName(), element.getName());

        Package pkg = srcElement._package();
        if (pkg == null)
        {
            Assert.assertNull(path, element._package());
        }
        else
        {
            Assert.assertEquals(path, getUserPath(pkg), getUserPath(element._package()));
        }

        org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.generics.GenericType classifierGenericType = srcElement._classifierGenericType();
        if (classifierGenericType == null)
        {
            Assert.assertNull(path, element._classifierGenericType());
        }
        else
        {
            Assert.assertEquals(path, GenericType.print(classifierGenericType, true, processorSupport), GenericType.print(element._classifierGenericType(), true, processorSupport));
        }

        Assert.assertEquals(path, getStereotypeSpecs(srcElement._stereotypes()), getStereotypeSpecs(element._stereotypes()));
        Assert.assertEquals(path, getTaggedValueSpecs(srcElement._taggedValues()), getTaggedValueSpecs(element._taggedValues()));

        Assert.assertEquals(path, getRefUsageSpecs(srcElement._referenceUsages()).sortThis(), getRefUsageSpecs(element._referenceUsages()).sortThis());

        if (srcElement instanceof ElementWithConstraints)
        {
            Assert.assertTrue(path, element instanceof ElementWithConstraints);
            ElementWithConstraints srcElementWithConstraints = (ElementWithConstraints) srcElement;
            ElementWithConstraints elementWithConstraints = (ElementWithConstraints) element;
            Assert.assertEquals(path, getConstraintSpecs(srcElementWithConstraints._constraints()), getConstraintSpecs(elementWithConstraints._constraints()));
        }
        else
        {
            Assert.assertFalse(path, element instanceof ElementWithConstraints);
        }

        if (srcElement instanceof Package)
        {
            Assert.assertTrue(path, element instanceof Package);
            Package srcPackage = (Package) srcElement;
            Package pkgElement = (Package) element;

            Assert.assertEquals(path,
                    srcPackage._children().collect(PackageableElement::_name, Lists.mutable.empty()).sortThis(),
                    pkgElement._children().collect(PackageableElement::_name, Lists.mutable.empty()).sortThis());
        }
        else
        {
            Assert.assertFalse(path, element instanceof Package);
        }

        if (srcElement instanceof Type)
        {
            Assert.assertTrue(path, element instanceof Type);
            Type srcType = (Type) srcElement;
            Type type = (Type) element;

            Assert.assertEquals(path, getGeneralizationSpecs(srcType._generalizations()), getGeneralizationSpecs(type._generalizations()));
            Assert.assertEquals(path, getSpecializationSpecs(srcType._specializations()).sortThis(), getSpecializationSpecs(type._specializations()).sortThis());
        }
        else
        {
            Assert.assertFalse(path, element instanceof Type);
        }

        if (srcElement instanceof Class)
        {
            Assert.assertTrue(path, element instanceof Class);
            Class<?> srcClass = (Class<?>) srcElement;
            Class<?> cls = (Class<?>) element;

            Assert.assertEquals(path, getPropertySpecs(srcClass._properties()), getPropertySpecs(cls._properties()));
            Assert.assertEquals(path, getQualifiedPropertySpecs(srcClass._qualifiedProperties()), getQualifiedPropertySpecs(cls._qualifiedProperties()));
            Assert.assertEquals(path, getPropertySpecs(srcClass._propertiesFromAssociations()), getPropertySpecs(cls._propertiesFromAssociations()));
            Assert.assertEquals(path, getQualifiedPropertySpecs(srcClass._qualifiedPropertiesFromAssociations()), getQualifiedPropertySpecs(cls._qualifiedPropertiesFromAssociations()));
            Assert.assertEquals(path, getVariableExpressionSpecs(srcClass._typeVariables()), getVariableExpressionSpecs(cls._typeVariables()));
        }
        else
        {
            Assert.assertFalse(path, element instanceof Class);
        }

        if (srcElement instanceof PrimitiveType)
        {
            Assert.assertTrue(path, element instanceof PrimitiveType);
            PrimitiveType srcPrimitiveType = (PrimitiveType) srcElement;
            PrimitiveType primitiveType = (PrimitiveType) element;

            Assert.assertEquals(path, srcPrimitiveType._extended(), primitiveType._extended());
            Assert.assertEquals(path, getVariableExpressionSpecs(srcPrimitiveType._typeVariables()), getVariableExpressionSpecs(primitiveType._typeVariables()));
        }
        else
        {
            Assert.assertFalse(path, element instanceof PrimitiveType);
        }

        if (srcElement instanceof Enumeration)
        {
            Assert.assertTrue(path, element instanceof Enumeration);
            Enumeration<? extends Enum> srcEnumeration = (Enumeration<? extends Enum>) srcElement;
            Enumeration<? extends Enum> enumeration = (Enumeration<? extends Enum>) element;

            Assert.assertEquals(path, srcEnumeration._values().collect(Enum::_name, Lists.mutable.empty()), enumeration._values().collect(Enum::_name, Lists.mutable.empty()));
        }
        else
        {
            Assert.assertFalse(path, element instanceof Enumeration);
        }

        if (srcElement instanceof Association)
        {
            Assert.assertTrue(path, element instanceof Association);
            Association srcAssociation = (Association) srcElement;
            Association association = (Association) element;

            Assert.assertEquals(path, getPropertySpecs(srcAssociation._properties()), getPropertySpecs(association._properties()));
            Assert.assertEquals(path, getQualifiedPropertySpecs(srcAssociation._qualifiedProperties()), getQualifiedPropertySpecs(association._qualifiedProperties()));
        }
        else
        {
            Assert.assertFalse(path, element instanceof Association);
        }

        if (srcElement instanceof Measure)
        {
            Assert.assertTrue(path, element instanceof Measure);
            Measure srcMeasure = (Measure) srcElement;
            Measure measure = (Measure) element;

            Assert.assertEquals(srcMeasure._canonicalUnit()._name(), measure._canonicalUnit()._name());
            Assert.assertEquals(srcMeasure._nonCanonicalUnits().collect(Unit::_name, Lists.mutable.empty()), measure._nonCanonicalUnits().collect(Unit::_name, Lists.mutable.empty()));
        }
        else
        {
            Assert.assertFalse(path, element instanceof Measure);
        }

        if (srcElement instanceof FunctionDefinition)
        {
            Assert.assertTrue(path, element instanceof FunctionDefinition);
            FunctionDefinition<?> srcFunc = (FunctionDefinition<?>) srcElement;
            FunctionDefinition<?> func = (FunctionDefinition<?>) element;

            FunctionType srcFuncType = (FunctionType) srcFunc._classifierGenericType()._typeArguments().getOnly()._rawType();
            FunctionType funcType = (FunctionType) func._classifierGenericType()._typeArguments().getOnly()._rawType();
            Assert.assertEquals(path, getVariableExpressionSpecs(srcFuncType._parameters()), getVariableExpressionSpecs(funcType._parameters()));

            Assert.assertEquals(path, srcFunc._applications().size(), func._applications().size());
            Assert.assertEquals(path, srcFunc._expressionSequence().size(), func._expressionSequence().size());

            Assert.assertEquals(path, getVariableExpressionSpecs(srcFuncType._parameters()), getVariableExpressionSpecs(funcType._parameters()));
            Assert.assertEquals(path, GenericType.print(srcFuncType._returnType(), true, processorSupport), GenericType.print(funcType._returnType(), true, processorSupport));
        }
        else
        {
            Assert.assertFalse(path, element instanceof FunctionDefinition);
        }
    }

    private static MutableList<String> getGeneralizationSpecs(RichIterable<? extends Generalization> generalizations)
    {
        return generalizations.collect(TestCompiledElementBuilder::getGeneralizationSpec, Lists.mutable.ofInitialCapacity(generalizations.size()));
    }

    private static String getGeneralizationSpec(Generalization generalization)
    {
        return GenericType.print(generalization._general(), true, processorSupport);
    }

    private static MutableList<String> getSpecializationSpecs(RichIterable<? extends Generalization> specializations)
    {
        return specializations.collect(TestCompiledElementBuilder::getSpecializationSpec, Lists.mutable.ofInitialCapacity(specializations.size()));
    }

    private static String getSpecializationSpec(Generalization specialization)
    {
        Type specific = specialization._specific();
        if (specific instanceof PackageableElement)
        {
            return getUserPath(specific);
        }
        if (specific instanceof ModelElement)
        {
            return appendUserPath(new StringBuilder(((ModelElement) specific)._name()).append(" instance of "), specific.getClassifier()).toString();
        }
        return appendUserPath(new StringBuilder(specific.getName()).append(" instance of "), specific.getClassifier()).toString();
    }

    private static MutableList<String> getPropertySpecs(RichIterable<? extends Property<?, ?>> properties)
    {
        return properties.collect(TestCompiledElementBuilder::getPropertySpec, Lists.mutable.ofInitialCapacity(properties.size()));
    }

    private static String getPropertySpec(Property<?, ?> property)
    {
        StringBuilder builder = new StringBuilder(property._name());
        GenericType.print(builder.append(':'), property._genericType(), true, processorSupport);
        Multiplicity.print(builder, property._multiplicity(), true);
        return builder.toString();
    }

    private static MutableList<String> getQualifiedPropertySpecs(RichIterable<? extends QualifiedProperty<?>> qualifiedProperties)
    {
        return qualifiedProperties.collect(TestCompiledElementBuilder::getQualifiedPropertySpec, Lists.mutable.ofInitialCapacity(qualifiedProperties.size()));
    }

    private static String getQualifiedPropertySpec(QualifiedProperty<?> qualifiedProperty)
    {
        StringBuilder builder = new StringBuilder(qualifiedProperty._functionName()).append('(');
        ((FunctionType) qualifiedProperty._classifierGenericType()._typeArguments().getOnly()._rawType())._parameters()
                .forEach(p -> appendVariableExpressionSpec(builder, p).append(", "));
        if (builder.charAt(builder.length() - 1) == ' ')
        {
            builder.setLength(builder.length() - 2);
        }
        GenericType.print(builder.append("):"), qualifiedProperty._genericType(), true, processorSupport);
        Multiplicity.print(builder, qualifiedProperty._multiplicity(), true);
        return builder.toString();
    }

    private static MutableList<String> getStereotypeSpecs(RichIterable<? extends Stereotype> stereotypes)
    {
        return stereotypes.collect(TestCompiledElementBuilder::getStereotypeSpec, Lists.mutable.ofInitialCapacity(stereotypes.size()));
    }

    private static String getStereotypeSpec(Stereotype stereotype)
    {
        return appendUserPath(new StringBuilder(), stereotype._profile()).append('.').append(stereotype._value()).toString();
    }

    private static MutableList<String> getTaggedValueSpecs(RichIterable<? extends TaggedValue> taggedValues)
    {
        return taggedValues.collect(TestCompiledElementBuilder::getTaggedValueSpec, Lists.mutable.ofInitialCapacity(taggedValues.size()));
    }

    private static String getTaggedValueSpec(TaggedValue taggedValue)
    {
        Tag tag = taggedValue._tag();
        return appendUserPath(new StringBuilder(), tag._profile())
                .append('.')
                .append(tag._value())
                .append("='")
                .append(taggedValue._value())
                .append('\'')
                .toString();
    }

    private static MutableList<String> getVariableExpressionSpecs(RichIterable<? extends VariableExpression> vars)
    {
        return vars.collect(TestCompiledElementBuilder::getVariableExpressionSpec, Lists.mutable.ofInitialCapacity(vars.size()));
    }

    private static String getVariableExpressionSpec(VariableExpression var)
    {
        return appendVariableExpressionSpec(new StringBuilder(), var).toString();
    }

    private static StringBuilder appendVariableExpressionSpec(StringBuilder builder, VariableExpression var)
    {
        builder.append(var._name());
        GenericType.print(builder.append(':'), var._genericType(), true, processorSupport);
        Multiplicity.print(builder, var._multiplicity(), true);
        return builder;
    }

    private static MutableList<String> getRefUsageSpecs(RichIterable<? extends ReferenceUsage> refUsages)
    {
        return refUsages.collect(TestCompiledElementBuilder::getRefUsageSpec, Lists.mutable.ofInitialCapacity(refUsages.size()));
    }

    private static String getRefUsageSpec(ReferenceUsage refUsage)
    {
        // we don't use the owner here to avoid resolving the reference, which can be expensive when there are a large number
        return "ReferenceUsage{property=" + refUsage._propertyName() + " offset=" + refUsage._offset() + " }";
    }

    private static MutableList<String> getConstraintSpecs(RichIterable<? extends Constraint> constraints)
    {
        return constraints.collect(TestCompiledElementBuilder::getConstraintSpec, Lists.mutable.ofInitialCapacity(constraints.size()));
    }

    private static String getConstraintSpec(Constraint constraint)
    {
        // TODO improve this
        return constraint._name();
    }

    private static String getUserPath(CoreInstance element)
    {
        return org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.getUserPathForPackageableElement(element);
    }

    private static StringBuilder appendUserPath(StringBuilder builder, CoreInstance element)
    {
        return org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement.writeUserPathForPackageableElement(builder, element);
    }

    private static class ElementBuilderWrapper implements ElementBuilder
    {
        private final MutableSet<String> concreteElements = Sets.mutable.<String>empty().asSynchronized();
        private final MutableSet<String> virtualPackages = Sets.mutable.<String>empty().asSynchronized();
        private final ElementBuilder delegate;

        private ElementBuilderWrapper(ElementBuilder delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Package buildVirtualPackage(VirtualPackageMetadata metadata, MetadataIndex index, ReferenceIdResolvers referenceIds)
        {
            this.virtualPackages.add(metadata.getPath());
            return this.delegate.buildVirtualPackage(metadata, index, referenceIds);
        }

        @Override
        public org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PackageableElement buildConcreteElement(ConcreteElementMetadata metadata, MetadataIndex index, ReferenceIdResolvers referenceIds, Supplier<? extends DeserializedConcreteElement> deserializer)
        {
            this.concreteElements.add(metadata.getPath());
            return this.delegate.buildConcreteElement(metadata, index, referenceIds, deserializer);
        }

        @Override
        public CoreInstance buildComponentInstance(InstanceData instanceData, MetadataIndex index, ReferenceIdResolver referenceIdResolver, IntFunction<? extends CoreInstance> internalIdResolver)
        {
            throw new UnsupportedOperationException("This should not be called directly");
        }
    }
}

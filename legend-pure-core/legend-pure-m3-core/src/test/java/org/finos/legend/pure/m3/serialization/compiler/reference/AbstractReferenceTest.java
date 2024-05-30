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

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.tuple.Pair;
import org.eclipse.collections.impl.tuple.Tuples;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.PropertyOwner;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.Property;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.function.property.QualifiedProperty;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.relationship.Association;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Class;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.serialization.filesystem.repository.CodeRepository;
import org.finos.legend.pure.m3.serialization.filesystem.repository.GenericCodeRepository;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.classpath.ClassLoaderCodeStorage;
import org.finos.legend.pure.m3.serialization.filesystem.usercodestorage.composite.CompositeCodeStorage;
import org.finos.legend.pure.m3.tests.AbstractPureTestWithCoreCompiled;
import org.finos.legend.pure.m3.tools.ListHelper;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.junit.Assert;
import org.junit.BeforeClass;

public class AbstractReferenceTest extends AbstractPureTestWithCoreCompiled
{
    @BeforeClass
    public static void setUpRuntime()
    {
        setUpRuntime(getFunctionExecution(), new CompositeCodeStorage(new ClassLoaderCodeStorage(getCodeRepositories())), getExtra());
    }

    protected static RichIterable<? extends CodeRepository> getCodeRepositories()
    {
        return Lists.mutable.<CodeRepository>withAll(AbstractPureTestWithCoreCompiled.getCodeRepositories())
                .with(GenericCodeRepository.build("ref_test", "test(::.*)?", "platform"));
    }

    public static Pair<String, String> getExtra()
    {
        return Tuples.pair(
                "/ref_test/test.pure",
                "import test::model::*;\n" +
                        "\n" +
                        "Class test::model::SimpleClass\n" +
                        "{\n" +
                        "  name : String[1];\n" +
                        "  id : Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class test::model::Left\n" +
                        "{\n" +
                        "  name : String[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class test::model::Right\n" +
                        "{\n" +
                        "  id : Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Association test::model::LeftRight\n" +
                        "{\n" +
                        "  toLeft : Left[*];\n" +
                        "  toLeft(name:String[1])\n" +
                        "  {\n" +
                        "    $this.toLeft->filter(l | $l.name == $name)\n" +
                        "  } : Left[*];\n" +
                        "  toRight : Right[*];" +
                        "  toRight(id:Integer[1])\n" +
                        "  {\n" +
                        "    $this.toRight->filter(r | $r.id == $id)\n" +
                        "  } : Right[*];\n" +
                        "}\n" +
                        "\n" +
                        "Profile test::model::SimpleProfile\n" +
                        "{\n" +
                        "  stereotypes : [st1, st2];\n" +
                        "  tags : [t1, t2, t3];\n" +
                        "}\n" +
                        "\n" +
                        "Enum test::model::SimpleEnumeration\n" +
                        "{\n" +
                        "  VAL1, VAL2\n" +
                        "}\n" +
                        "\n" +
                        "Class test::model::BothSides extends Left, Right\n" +
                        "{\n" +
                        "  leftCount : Integer[1];\n" +
                        "  rightCount : Integer[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class <<doc.deprecated>> {doc.doc = 'Deprecated class with annotations'} test::model::ClassWithAnnotations\n" +
                        "{\n" +
                        "  <<doc.deprecated>> deprecated : String[0..1];\n" +
                        "  <<doc.deprecated>> {doc.doc = 'Deprecated: don\\'t use this'} alsoDeprecated : String[0..1];\n" +
                        "  {doc.doc = 'Time must be specified', doc.todo = 'Change this to DateTime'} date : Date[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class test::model::ClassWithTypeAndMultParams<T,V|m,n>\n" +
                        "{\n" +
                        "  propT : T[m];\n" +
                        "  propV : V[n];\n" +
                        "}\n" +
                        "\n" +
                        "Class test::model::ClassWithQualifiedProperties\n" +
                        "{\n" +
                        "  names : String[*];\n" +
                        "  title : String[0..1];\n" +
                        "  firstName()\n" +
                        "  {\n" +
                        "    if($this.names->isEmpty(), |'', |$this.names->at(0))\n" +
                        "  }:String[1];\n" +
                        "  fullName()\n" +
                        "  {\n" +
                        "    $this.fullName(false)\n" +
                        "  }:String[1];\n" +
                        "  fullName(withTitle:Boolean[1])\n" +
                        "  {\n" +
                        "    let titleString = if($withTitle && !$this.title->isEmpty(), |$this.title->toOne() + ' ', |'');\n" +
                        "    $this.names->joinStrings($titleString, ' ', '');\n" +
                        "  }:String[1];\n" +
                        "}\n" +
                        "\n" +
                        "Class <<temporal.businesstemporal>> test::model::ClassWithMilestoning1\n" +
                        "{\n" +
                        "   toClass2:ClassWithMilestoning2[1];\n" +
                        "   toClass3:ClassWithMilestoning3[*];\n" +
                        "}\n" +
                        "\n" +
                        "Class <<temporal.processingtemporal>> test::model::ClassWithMilestoning2\n" +
                        "{\n" +
                        "   toClass1:ClassWithMilestoning1[1];\n" +
                        "   toClass3:ClassWithMilestoning3[*];\n" +
                        "}\n" +
                        "\n" +
                        "Class <<temporal.bitemporal>> test::model::ClassWithMilestoning3\n" +
                        "{\n" +
                        "   toClass1:ClassWithMilestoning1[0..1];\n" +
                        "   toClass2:ClassWithMilestoning2[0..1];\n" +
                        "}\n" +
                        "\n" +
                        "Association test::model::AssociationWithMilestoning1\n" +
                        "{\n" +
                        "   toClass1A:ClassWithMilestoning1[*];\n" +
                        "   toClass2A:ClassWithMilestoning2[*];\n" +
                        "}\n" +
                        "\n" +
                        "Association test::model::AssociationWithMilestoning2\n" +
                        "{\n" +
                        "   toClass1B:ClassWithMilestoning1[*];\n" +
                        "   toClass3B:ClassWithMilestoning3[*];\n" +
                        "}\n" +
                        "\n" +
                        "Association test::model::AssociationWithMilestoning3\n" +
                        "{\n" +
                        "   toClass2C:ClassWithMilestoning2[*];\n" +
                        "   toClass3C:ClassWithMilestoning3[*];\n" +
                        "}\n" +
                        "\n" +
                        "function test::model::testFunc<T|m>(col:T[m], func:Function<{T[1]->String[1]}>[0..1]):String[m]\n" +
                        "{\n" +
                        "  let toStringFunc = if($func->isEmpty(), |{x:T[1] | $x->toString()}, |$func->toOne());\n" +
                        "  $col->map(x | $toStringFunc->eval($x));\n" +
                        "}\n" +
                        "\n" +
                        "function test::model::testFunc2():String[1]\n" +
                        "{\n" +
                        "  let pkg = test::model;\n" +
                        "  let unit = Mass~Pound;\n" +
                        "  $pkg->elementToPath() + '::' + $unit.measure.name->toOne() + '~' + $unit.name->toOne();\n" +
                        "}\n" +
                        "\n" +
                        "function test::model::testFunc3():Any[*]\n" +
                        "{\n" +
                        "  [test::model, 'a', 1, true, |test::model.children, %2024-11-04]\n" +
                        "}\n" +
                        "\n" +
                        "Measure test::model::Currency\n" +
                        "{\n" +
                        "  USD;\n" +
                        "  GBP;\n" +
                        "  EUR;\n" +
                        "}\n" +
                        "\n" +
                        "Measure test::model::Mass\n" +
                        "{\n" +
                        "  *Gram: x -> $x;\n" +
                        "  Kilogram: x -> $x*1000;\n" +
                        "  Pound: x -> $x*453.59;\n" +
                        "}\n"
        );
    }

    @SuppressWarnings("unchecked")
    protected static <T extends CoreInstance> T getCoreInstance(String path)
    {
        CoreInstance instance = runtime.getCoreInstance(path);
        Assert.assertNotNull(path, instance);
        return (T) instance;
    }

    protected static Property<?, ?> findProperty(PropertyOwner owner, String name)
    {
        RichIterable<? extends Property<?, ?>> properties = (owner instanceof Class) ? ((Class<?>) owner)._properties() : ((Association) owner)._properties();
        Property<?, ?> property = properties.detect(p -> name.equals(p._name()));
        if (property == null)
        {
            StringBuilder builder = new StringBuilder("Could not find property '").append(name).append("' for ");
            PackageableElement.writeUserPathForPackageableElement(builder, owner);
            if (properties.isEmpty())
            {
                builder.append("; no available properties");
            }
            else
            {
                properties.collect(Property::_name, Lists.mutable.empty()).sortThis().appendString(builder, "; available properties: '", "', '", "'");
            }
            Assert.fail(builder.toString());
        }
        return property;
    }

    protected static QualifiedProperty<?> findQualifiedProperty(PropertyOwner owner, String id)
    {
        RichIterable<? extends QualifiedProperty<?>> qualifiedProperties = (owner instanceof Class) ? ((Class<?>) owner)._qualifiedProperties() : ((Association) owner)._qualifiedProperties();
        QualifiedProperty<?> qualifiedProperty = qualifiedProperties.detect(qp -> id.equals(qp._id()));
        if (qualifiedProperty == null)
        {
            StringBuilder builder = new StringBuilder("Could not find qualified property '").append(id).append("' for ");
            PackageableElement.writeUserPathForPackageableElement(builder, owner);
            if (qualifiedProperties.isEmpty())
            {
                builder.append("; no available qualified properties");
            }
            else
            {
                qualifiedProperties.collect(QualifiedProperty::_id, Lists.mutable.empty()).sortThis().appendString(builder, "; available qualified properties: '", "', '", "'");
            }
            Assert.fail(builder.toString());
        }
        return qualifiedProperty;
    }

    protected static <T> ListIterable<T> toList(Iterable<T> iterable)
    {
        return ListHelper.wrapListIterable(iterable);
    }
}

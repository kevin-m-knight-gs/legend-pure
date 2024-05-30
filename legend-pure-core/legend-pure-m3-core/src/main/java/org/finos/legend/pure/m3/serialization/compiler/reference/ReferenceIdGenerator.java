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
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.map.ConcurrentMutableMap;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MapIterable;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;
import org.finos.legend.pure.m3.coreinstance.helper.AnyStubHelper;
import org.finos.legend.pure.m3.coreinstance.helper.PropertyTypeHelper;
import org.finos.legend.pure.m3.coreinstance.meta.pure.metamodel.type.Any;
import org.finos.legend.pure.m3.navigation.Instance;
import org.finos.legend.pure.m3.navigation.M3Paths;
import org.finos.legend.pure.m3.navigation.M3Properties;
import org.finos.legend.pure.m3.navigation.M3PropertyPaths;
import org.finos.legend.pure.m3.navigation.PackageableElement.PackageableElement;
import org.finos.legend.pure.m3.navigation.PrimitiveUtilities;
import org.finos.legend.pure.m3.navigation.ProcessorSupport;
import org.finos.legend.pure.m3.navigation._package._Package;
import org.finos.legend.pure.m3.navigation.graph.GraphPath;
import org.finos.legend.pure.m3.navigation.multiplicity.Multiplicity;
import org.finos.legend.pure.m3.navigation.property.Property;
import org.finos.legend.pure.m3.navigation.type.Type;
import org.finos.legend.pure.m4.coreinstance.CoreInstance;
import org.finos.legend.pure.m4.coreinstance.SourceInformation;
import org.finos.legend.pure.m4.serialization.grammar.StringEscape;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Objects;

public class ReferenceIdGenerator
{
    private static final ImmutableMap<String, ImmutableList<String>> SKIP_PROPERTY_PATHS = M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS
            .groupByUniqueKey(ImmutableList::getLast, Maps.mutable.ofInitialCapacity(M3PropertyPaths.BACK_REFERENCE_PROPERTY_PATHS.size() + 2))
            .withKeyValue(M3PropertyPaths._package.getLast(), M3PropertyPaths._package)
            .withKeyValue(M3PropertyPaths.children.getLast(), M3PropertyPaths.children)
            .toImmutable();

    private final ProcessorSupport processorSupport;
    private final TypeCache typeCache;

    public ReferenceIdGenerator(ProcessorSupport processorSupport)
    {
        this.processorSupport = Objects.requireNonNull(processorSupport, "processorSupport is required");
        this.typeCache = new TypeCache(this.processorSupport);
    }

    public MapIterable<CoreInstance, String> generateIdsForElement(String path)
    {
        CoreInstance element = this.processorSupport.package_getByUserPath(Objects.requireNonNull(path, "path may not be null"));
        if (element == null)
        {
            throw new IllegalArgumentException("Could not find element: \"" + path + "\"");
        }
        validateIsPackageableElement(element);
        return generateIdsForElement(element, path);
    }

    public MapIterable<CoreInstance, String> generateIdsForElement(CoreInstance element)
    {
        validateIsPackageableElement(Objects.requireNonNull(element, "element may not be null"));
        return generateIdsForElement(element, PackageableElement.getUserPathForPackageableElement(element));
    }

    private MapIterable<CoreInstance, String> generateIdsForElement(CoreInstance element, String path)
    {
        // We have already validated that element is a PackageableElement
        if ((path == null) || path.isEmpty())
        {
            throw new IllegalArgumentException("Invalid path: path may not be null or empty");
        }
        String elementName = PrimitiveUtilities.getStringValue(element.getValueForMetaPropertyToOne(M3Properties.name));
        if ((element.getName() == null) || (elementName == null))
        {
            throw new IllegalArgumentException("Invalid element '" + path + "': name is null");
        }
        if (!element.getName().equals(elementName))
        {
            throw new IllegalArgumentException("Invalid element '" + path + "': instance name ('" + StringEscape.escape(element.getName()) + "') does not match name property ('" + StringEscape.escape(elementName) + "')");
        }
        if (!(path.equals(elementName) || (path.endsWith(elementName) && (path.charAt(path.length() - elementName.length() - 1) == ':'))))
        {
            throw new IllegalArgumentException("Invalid path for element named '" + StringEscape.escape(elementName) + "': '" + path + "'");
        }
        if (element.getSourceInformation() == null)
        {
            // We allow null source info only for virtual packages
            if (_Package.isPackage(element, this.processorSupport))
            {
                return Maps.immutable.with(element, path);
            }
            throw new IllegalArgumentException("No source information for '" + path + "'");
        }
        return new Generator(element, path, this.typeCache).generateIds();
    }

    private void validateIsPackageableElement(CoreInstance element)
    {
        if (!PackageableElement.isPackageableElement(element, this.processorSupport))
        {
            StringBuilder builder = new StringBuilder("Expected a PackageableElement, got instance of: ");
            if (element instanceof Any)
            {
                String systemPath = ((Any) element).getFullSystemPath();
                builder.append(systemPath, "Root::".length(), systemPath.length());
            }
            else
            {
                CoreInstance classifier = this.processorSupport.getClassifier(element);
                PackageableElement.writeUserPathForPackageableElement(builder, classifier);
            }
            throw new IllegalArgumentException(builder.toString());
        }
    }

    private static class Generator
    {
        private final SourceInformation sourceInfo;
        private final Deque<SearchNode> deque = new ArrayDeque<>();
        private final TypeCache typeCache;

        private Generator(CoreInstance element, String path, TypeCache typeCache)
        {
            this.sourceInfo = element.getSourceInformation();
            this.typeCache = typeCache;
            enqueue(GraphPath.buildPath(path, false), Lists.immutable.with(element), this.typeCache.getClassifier(element));
        }

        private MapIterable<CoreInstance, String> generateIds()
        {
            MapIterable<CoreInstance, GraphPath> paths = generateIdGraphPaths();
            MutableMap<CoreInstance, String> result = Maps.mutable.ofInitialCapacity(paths.size());
            paths.forEachKeyValue((k, v) -> result.put(k, v.getDescription()));
            return result;
        }

        private MapIterable<CoreInstance, GraphPath> generateIdGraphPaths()
        {
            MutableMap<CoreInstance, GraphPath> paths = Maps.mutable.empty();
            while (!this.deque.isEmpty())
            {
                SearchNode searchNode = this.deque.pollFirst();
                CoreInstance instance = searchNode.pathNodes.getLast();
                if ((instance.getSourceInformation() == null) || this.typeCache.isStubType(searchNode.finalNodeClassifier))
                {
                    // No source information or a Stub instance: no need to record a graph path, but we should still
                    // advance from the search node.
                    advanceFromSearchNode(searchNode);
                }
                else
                {
                    // If we have not recorded a graph path for this instance before, or we have a better path, then
                    // record the graph path and advance. Otherwise, we have already been to this instance with a better
                    // graph path, so there is no point in continuing the search from here.
                    GraphPath oldGraphPath = paths.get(instance);
                    if ((oldGraphPath == null) || (compareGraphPaths(searchNode.path, oldGraphPath) < 0))
                    {
                        paths.put(instance, searchNode.path);
                        advanceFromSearchNode(searchNode);
                    }
                }
            }
            return paths;
        }

        private void advanceFromSearchNode(SearchNode searchNode)
        {
            CoreInstance instance = searchNode.pathNodes.getLast();
            this.typeCache.getSimplePropertyInfo(searchNode.finalNodeClassifier).forEachKeyValue((propertyName, propertyInfo) ->
            {
                if (propertyInfo.shouldSkip())
                {
                    return;
                }

                // To-one property handling
                if (propertyInfo.isToOne())
                {
                    CoreInstance value = instance.getValueForMetaPropertyToOne(propertyName);
                    if ((value != null) && isInternal(value) && !searchNode.pathNodes.contains(value))
                    {
                        CoreInstance valueClassifier = this.typeCache.getClassifier(value);
                        if (!this.typeCache.isPrimitiveType(valueClassifier))
                        {
                            GraphPath newGraphPath = searchNode.path.withToOneProperty(propertyName, false);
                            ImmutableList<CoreInstance> newPathNodes = searchNode.pathNodes.newWith(value);
                            enqueue(newGraphPath, newPathNodes, valueClassifier);
                        }
                    }
                    return;
                }

                // To-many property handling
                ListIterable<? extends CoreInstance> values = instance.getValueForMetaPropertyToMany(propertyName);
                if (values.notEmpty())
                {
                    Collection<CoreInstance> pathNodes = ((values.size() > 1) && (searchNode.pathNodes.size() > 8)) ? Sets.mutable.withAll(searchNode.pathNodes) : searchNode.pathNodes.castToCollection();
                    PropertyIndex index = tryIndex(propertyInfo, values);
                    if (index == null)
                    {
                        values.forEachWithIndex((value, i) ->
                        {
                            if (isInternal(value) && !pathNodes.contains(value))
                            {
                                CoreInstance valueClassifier = this.typeCache.getClassifier(value);
                                if (!this.typeCache.isPrimitiveType(valueClassifier))
                                {
                                    GraphPath newGraphPath = searchNode.path.withToManyPropertyValueAtIndex(propertyName, i, false);
                                    ImmutableList<CoreInstance> newPathNodes = searchNode.pathNodes.newWith(value);
                                    enqueue(newGraphPath, newPathNodes, valueClassifier);
                                }
                            }
                        });
                    }
                    else
                    {
                        index.index.forEachKeyValue((name, value) ->
                        {
                            if (isInternal(value) && !pathNodes.contains(value))
                            {
                                // The fact that we built an index implies these are not primitive values
                                CoreInstance valueClassifier = this.typeCache.getClassifier(value);
                                GraphPath newGraphPath = searchNode.path.withToManyPropertyValueWithKey(propertyName, index.property, name, false);
                                ImmutableList<CoreInstance> newPathNodes = searchNode.pathNodes.newWith(value);
                                enqueue(newGraphPath, newPathNodes, valueClassifier);
                            }
                        });
                    }
                }
            });
        }

        private void enqueue(GraphPath path, ImmutableList<CoreInstance> pathNodes, CoreInstance finalNodeClassifier)
        {
            this.deque.addLast(new SearchNode(path, pathNodes, finalNodeClassifier));
        }

        private boolean isInternal(CoreInstance instance)
        {
            SourceInformation sourceInfo = instance.getSourceInformation();
            return (sourceInfo == null) || this.sourceInfo.subsumes(sourceInfo);
        }

        private PropertyIndex tryIndex(PropertyInfo propertyInfo, ListIterable<? extends CoreInstance> values)
        {
            // Figure out which key to index by (if any)
            String keyProp = propertyInfo.getIndexKey();
            if (keyProp == null)
            {
                return null;
            }

            //  Check that the key is present, to-one, and has type of String
            PropertyInfo keyPropInfo = this.typeCache.getSimplePropertyInfo(propertyInfo.getRawType()).get(keyProp);
            if ((keyPropInfo == null) || !keyPropInfo.isToOne() || !this.typeCache.isStringType(keyPropInfo.getRawType()))
            {
                return null;
            }

            // Try to build an index
            MutableMap<String, CoreInstance> index = Maps.mutable.ofInitialCapacity(values.size());
            for (CoreInstance value : values)
            {
                String key = PrimitiveUtilities.getStringValue(value.getValueForMetaPropertyToOne(keyProp), null);
                if ((key == null) || (index.put(key, value) != null))
                {
                    // Either no key exists for the value or there is a clash: cannot build an index
                    return null;
                }
            }
            return new PropertyIndex(keyProp, index);
        }

        private static int compareGraphPaths(GraphPath path1, GraphPath path2)
        {
            if (path1 == path2)
            {
                return 0;
            }

            int edgeCount = path1.getEdgeCount();
            int cmp = Integer.compare(edgeCount, path2.getEdgeCount());
            for (int i = 0; (cmp == 0) && (i < edgeCount); i++)
            {
                cmp = compareEdges(path1.getEdge(i), path2.getEdge(i));
            }
            return cmp;
        }

        private static int compareEdges(GraphPath.Edge edge1, GraphPath.Edge edge2)
        {
            // to-one < to-many[key] < to-many[index]
            return edge1.visit(new GraphPath.EdgeVisitor<Integer>()
            {
                @Override
                public Integer visit(GraphPath.ToOnePropertyEdge e1)
                {
                    return (edge2 instanceof GraphPath.ToOnePropertyEdge) ? compareStrings(e1.getProperty(), edge2.getProperty()) : -1;
                }

                @Override
                public Integer visit(GraphPath.ToManyPropertyAtIndexEdge e1)
                {
                    return edge2.visit(new GraphPath.EdgeVisitor<Integer>()
                    {
                        @Override
                        public Integer visit(GraphPath.ToOnePropertyEdge e2)
                        {
                            return 1;
                        }

                        @Override
                        public Integer visit(GraphPath.ToManyPropertyAtIndexEdge e2)
                        {
                            int cmp = compareStrings(e1.getProperty(), e2.getProperty());
                            return (cmp != 0) ? cmp : Integer.compare(e1.getIndex(), e2.getIndex());
                        }

                        @Override
                        public Integer visit(GraphPath.ToManyPropertyWithStringKeyEdge e2)
                        {
                            return 1;
                        }
                    });
                }

                @Override
                public Integer visit(GraphPath.ToManyPropertyWithStringKeyEdge e1)
                {
                    return edge2.visit(new GraphPath.EdgeVisitor<Integer>()
                    {
                        @Override
                        public Integer visit(GraphPath.ToOnePropertyEdge e2)
                        {
                            return 1;
                        }

                        @Override
                        public Integer visit(GraphPath.ToManyPropertyAtIndexEdge e2)
                        {
                            return -1;
                        }

                        @Override
                        public Integer visit(GraphPath.ToManyPropertyWithStringKeyEdge e2)
                        {
                            int cmp = compareStrings(e1.getProperty(), e2.getProperty());
                            if (cmp == 0)
                            {
                                cmp = compareStrings(e1.getKeyProperty(), e2.getKeyProperty());
                                if (cmp == 0)
                                {
                                    cmp = compareStrings(e1.getKey(), e2.getKey());
                                }
                            }
                            return cmp;
                        }
                    });
                }
            });
        }

        private static int compareStrings(String string1, String string2)
        {
            int cmp = Integer.compare(string1.length(), string2.length());
            return (cmp != 0) ? cmp : string1.compareTo(string2);
        }
    }

    private static class TypeCache
    {
        private final ProcessorSupport processorSupport;
        private final ImmutableSet<CoreInstance> stubClasses;
        private final ImmutableSet<CoreInstance> primitiveTypes;
        private final CoreInstance stringType;
        private final CoreInstance enumerationClass;
        private final CoreInstance qualifiedPropertyClass;
        private final CoreInstance stereotypeClass;
        private final CoreInstance tagClass;
        private final ConcurrentMutableMap<CoreInstance, MapIterable<String, PropertyInfo>> propertyInfoByClassByName = ConcurrentHashMap.newMap();

        private TypeCache(ProcessorSupport processorSupport)
        {
            this.processorSupport = processorSupport;
            this.stubClasses = Sets.immutable.withAll(AnyStubHelper.getStubClasses(processorSupport));
            this.primitiveTypes = Sets.immutable.withAll(PrimitiveUtilities.getPrimitiveTypes(processorSupport));
            this.stringType = this.processorSupport.repository_getTopLevel(M3Paths.String);
            this.enumerationClass = this.processorSupport.package_getByUserPath(M3Paths.Enumeration);
            this.qualifiedPropertyClass = this.processorSupport.package_getByUserPath(M3Paths.QualifiedProperty);
            this.stereotypeClass = this.processorSupport.package_getByUserPath(M3Paths.Stereotype);
            this.tagClass = this.processorSupport.package_getByUserPath(M3Paths.Tag);
        }

        CoreInstance getClassifier(CoreInstance instance)
        {
            return this.processorSupport.getClassifier(instance);
        }

        boolean isStubType(CoreInstance instance)
        {
            return this.stubClasses.contains(instance);
        }

        boolean isPrimitiveType(CoreInstance instance)
        {
            return this.primitiveTypes.contains(instance);
        }

        boolean isStringType(CoreInstance instance)
        {
            return this.stringType == instance;
        }

        MapIterable<String, PropertyInfo> getSimplePropertyInfo(CoreInstance classifier)
        {
            return this.propertyInfoByClassByName.getIfAbsentPutWithKey(classifier, this::computeSimplePropertyInfo);
        }

        private MapIterable<String, PropertyInfo> computeSimplePropertyInfo(CoreInstance classifier)
        {
            MapIterable<String, CoreInstance> properties = this.processorSupport.class_getSimplePropertiesByName(classifier);
            if (properties.isEmpty())
            {
                return Maps.immutable.empty();
            }

            CoreInstance classGenericType = Type.wrapGenericType(classifier, classifier.getSourceInformation(), this.processorSupport);
            MutableMap<String, PropertyInfo> map = Maps.mutable.empty();
            properties.forEachKeyValue((name, prop) -> map.put(name, computePropertyInfo(classGenericType, name, prop)));
            if (classifier == this.enumerationClass)
            {
                // Special handling for Enumeration: the type of values is T, but we know that T will always be a subclass of Enum
                PropertyInfo propInfo = map.get(M3Properties.values);
                if ((propInfo != null) && (propInfo.getRawType() == null))
                {
                    map.put(M3Properties.values, new ToManyPropertyInfoDefaultIndexKey(this.processorSupport.package_getByUserPath(M3Paths.Enum)));
                }
            }
            return map.isEmpty() ? Maps.immutable.empty() : map;
        }

        private PropertyInfo computePropertyInfo(CoreInstance classGenericType, String propertyName, CoreInstance property)
        {
            CoreInstance resolvedGenericType = PropertyTypeHelper.getPropertyResolvedReturnType(classGenericType, property, this.processorSupport);
            CoreInstance rawType = Instance.getValueForMetaPropertyToOneResolved(resolvedGenericType, M3Properties.rawType, this.processorSupport);

            CoreInstance resolvedMultiplicity = Property.resolvePropertyReturnMultiplicity(classGenericType, property, this.processorSupport);
            if (Multiplicity.isToOne(resolvedMultiplicity, false))
            {
                return shouldSkipProperty(propertyName, property) ? new ToOneSkipPropertyInfo(rawType) : new ToOnePropertyInfo(rawType);
            }

            if (rawType == null)
            {
                return new ToManyPropertyInfoNoIndexKey(null);
            }

            if (isPrimitiveType(rawType) || shouldSkipProperty(propertyName, property))
            {
                return new ToManySkipPropertyInfo(rawType);
            }

            String indexKey = getSpecialIndexKey(rawType);
            return (indexKey == null) ? new ToManyPropertyInfoDefaultIndexKey(rawType) : new ToManyPropertyInfoSpecialIndexKey(rawType, indexKey);
        }

        private boolean shouldSkipProperty(String propertyName, CoreInstance property)
        {
            ImmutableList<String> realKeyToSkip = SKIP_PROPERTY_PATHS.get(propertyName);
            return (realKeyToSkip != null) && realKeyToSkip.equals(Property.calculatePropertyPath(property, this.processorSupport));
        }

        private String getSpecialIndexKey(CoreInstance rawType)
        {
            if (this.qualifiedPropertyClass == rawType)
            {
                return M3Properties.id;
            }
            if ((this.stereotypeClass == rawType) || (this.tagClass == rawType))
            {
                return M3Properties.value;
            }
            return null;
        }
    }

    private static class SearchNode
    {
        private final GraphPath path;
        private final ImmutableList<CoreInstance> pathNodes;
        private final CoreInstance finalNodeClassifier;

        private SearchNode(GraphPath path, ImmutableList<CoreInstance> pathNodes, CoreInstance finalNodeClassifier)
        {
            this.path = path;
            this.pathNodes = pathNodes;
            this.finalNodeClassifier = finalNodeClassifier;
        }
    }

    private abstract static class PropertyInfo
    {
        private final CoreInstance rawType;

        private PropertyInfo(CoreInstance rawType)
        {
            this.rawType = rawType;
        }

        CoreInstance getRawType()
        {
            return this.rawType;
        }

        boolean shouldSkip()
        {
            return false;
        }

        abstract boolean isToOne();

        String getIndexKey()
        {
            return null;
        }
    }

    private static class ToOnePropertyInfo extends PropertyInfo
    {
        private ToOnePropertyInfo(CoreInstance rawType)
        {
            super(rawType);
        }

        @Override
        public boolean isToOne()
        {
            return true;
        }
    }

    private static class ToOneSkipPropertyInfo extends ToOnePropertyInfo
    {
        private ToOneSkipPropertyInfo(CoreInstance rawType)
        {
            super(rawType);
        }

        @Override
        public boolean shouldSkip()
        {
            return true;
        }
    }

    private abstract static class ToManyPropertyInfo extends PropertyInfo
    {
        private ToManyPropertyInfo(CoreInstance rawType)
        {
            super(rawType);
        }

        @Override
        public boolean isToOne()
        {
            return false;
        }
    }

    private static class ToManyPropertyInfoNoIndexKey extends ToManyPropertyInfo
    {
        private ToManyPropertyInfoNoIndexKey(CoreInstance rawType)
        {
            super(rawType);
        }
    }

    private static class ToManySkipPropertyInfo extends ToManyPropertyInfoNoIndexKey
    {
        private ToManySkipPropertyInfo(CoreInstance rawType)
        {
            super(rawType);
        }

        @Override
        boolean shouldSkip()
        {
            return true;
        }
    }

    private static class ToManyPropertyInfoDefaultIndexKey extends ToManyPropertyInfo
    {
        private ToManyPropertyInfoDefaultIndexKey(CoreInstance rawType)
        {
            super(rawType);
        }

        @Override
        String getIndexKey()
        {
            return M3Properties.name;
        }
    }

    private static class ToManyPropertyInfoSpecialIndexKey extends ToManyPropertyInfo
    {
        private final String indexKey;

        private ToManyPropertyInfoSpecialIndexKey(CoreInstance rawType, String indexKey)
        {
            super(rawType);
            this.indexKey = indexKey;
        }

        @Override
        String getIndexKey()
        {
            return this.indexKey;
        }
    }

    private static class PropertyIndex
    {
        private final String property;
        private final MapIterable<String, CoreInstance> index;

        private PropertyIndex(String property, MapIterable<String, CoreInstance> index)
        {
            this.property = property;
            this.index = index;
        }
    }
}

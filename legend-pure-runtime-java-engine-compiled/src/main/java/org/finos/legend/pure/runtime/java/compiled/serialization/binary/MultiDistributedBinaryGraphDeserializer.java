package org.finos.legend.pure.runtime.java.compiled.serialization.binary;

import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.impl.utility.Iterate;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.Obj;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjOrUpdate;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjOrUpdateConsumer;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjOrUpdateVisitor;
import org.finos.legend.pure.runtime.java.compiled.serialization.model.ObjUpdate;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class MultiDistributedBinaryGraphDeserializer
{
    private MultiDistributedBinaryGraphDeserializer()
    {
    }

    public abstract boolean hasClassifier(String classifierId);

    public abstract RichIterable<String> getClassifiers();

    public abstract boolean hasInstance(String classifierId, String instanceId);

    public abstract RichIterable<String> getClassifierInstanceIds(String classifierId);

    public final Obj getInstance(String classifierId, String instanceId)
    {
        return getInstance(classifierId, instanceId, true);
    }

    public final Obj getInstanceIfPresent(String classifierId, String instanceId)
    {
        return getInstance(classifierId, instanceId, false);
    }

    protected abstract Obj getInstance(String classifierId, String instanceId, boolean throwIfNotFound);

    public final ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds)
    {
        return getInstances(classifierId, instanceIds, true);
    }

    public final ListIterable<Obj> getInstancesIfPresent(String classifierId, Iterable<String> instanceIds)
    {
        return getInstances(classifierId, instanceIds, false);
    }

    protected abstract ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds, boolean throwIfNotFound);

    public static MultiDistributedBinaryGraphDeserializer fromDeserializers(DistributedBinaryGraphDeserializer... deserializers)
    {
        Objects.requireNonNull(deserializers, "deserializers may not be null");
        return newDeserializer(Lists.immutable.with(deserializers));
    }

    public static MultiDistributedBinaryGraphDeserializer fromDeserializers(Iterable<? extends DistributedBinaryGraphDeserializer> deserializers)
    {
        Objects.requireNonNull(deserializers, "deserializers may not be null");
        return newDeserializer(Lists.immutable.withAll(deserializers));
    }

    public static MultiDistributedBinaryGraphDeserializer fromClassLoader(Iterable<String> metadataNames, ClassLoader classLoader)
    {
        Objects.requireNonNull(metadataNames, "metadata names may not be null");
        Objects.requireNonNull(classLoader, "class loader may not be null");
        return fromFileReader(metadataNames, FileReaders.fromClassLoader(classLoader));
    }

    public static MultiDistributedBinaryGraphDeserializer fromFileReader(Iterable<String> metadataNames, FileReader fileReader)
    {
        Objects.requireNonNull(metadataNames, "metadata names may not be null");
        Objects.requireNonNull(fileReader, "file reader may not be null");
        return fromDeserializers(Iterate.collect(metadataNames, mn -> DistributedBinaryGraphDeserializer.fromFileReader(mn, fileReader), Lists.mutable.empty()));
    }

    private static MultiDistributedBinaryGraphDeserializer newDeserializer(ImmutableList<DistributedBinaryGraphDeserializer> deserializers)
    {
        deserializers.forEach(d -> Objects.requireNonNull(d, "deserializer may not be null"));
        switch (deserializers.size())
        {
            case 0:
            {
                return new Empty();
            }
            case 1:
            {
                return new Single(deserializers.get(0));
            }
            default:
            {
                return new Many(deserializers);
            }
        }
    }

    private static class Empty extends MultiDistributedBinaryGraphDeserializer
    {
        private Empty()
        {
        }

        @Override
        public boolean hasClassifier(String classifierId)
        {
            return false;
        }

        @Override
        public RichIterable<String> getClassifiers()
        {
            return Lists.immutable.empty();
        }

        @Override
        public boolean hasInstance(String classifierId, String instanceId)
        {
            return false;
        }

        @Override
        public RichIterable<String> getClassifierInstanceIds(String classifierId)
        {
            return Lists.immutable.empty();
        }

        @Override
        protected Obj getInstance(String classifierId, String instanceId, boolean throwIfNotFound)
        {
            if (throwIfNotFound)
            {
                throw new RuntimeException("Unknown instance: classifier='" + classifierId + "', id='" + instanceId + "'");
            }
            return null;
        }

        @Override
        protected ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds, boolean throwIfNotFound)
        {
            if (throwIfNotFound)
            {
                Set<String> instanceIdSet = (instanceIds instanceof Set) ? (Set<String>) instanceIds : Sets.mutable.withAll(instanceIds);
                if (!instanceIdSet.isEmpty())
                {
                    String message = (instanceIdSet.size() == 1) ?
                            "Unknown instance: classifier='" + classifierId + "', id='" + Iterate.getFirst(instanceIdSet) + "'" :
                            Lists.mutable.withAll(instanceIdSet).sortThis().makeString("Unknown instance: classifier='" + classifierId + "', ids='", "', '", "'");
                    throw new RuntimeException(message);
                }
            }
            return Lists.immutable.empty();
        }
    }

    private static class Single extends MultiDistributedBinaryGraphDeserializer
    {
        private final DistributedBinaryGraphDeserializer deserializer;

        private Single(DistributedBinaryGraphDeserializer deserializer)
        {
            this.deserializer = deserializer;
        }

        @Override
        public boolean hasClassifier(String classifierId)
        {
            return this.deserializer.hasClassifier(classifierId);
        }

        @Override
        public RichIterable<String> getClassifiers()
        {
            return this.deserializer.getClassifiers();
        }

        @Override
        public boolean hasInstance(String classifierId, String instanceId)
        {
            return this.deserializer.hasInstance(classifierId, instanceId);
        }

        @Override
        public RichIterable<String> getClassifierInstanceIds(String classifierId)
        {
            return this.deserializer.getClassifierInstanceIds(classifierId);
        }

        @Override
        protected Obj getInstance(String classifierId, String instanceId, boolean throwIfNotFound)
        {
            return this.deserializer.getInstance(classifierId, instanceId, throwIfNotFound).visit(new ObjOrUpdateVisitor<Obj>()
            {
                @Override
                public Obj visit(Obj obj)
                {
                    return obj;
                }

                @Override
                public Obj visit(ObjUpdate objUpdate)
                {
                    throw new RuntimeException("Cannot find main definition for instance: classifier='" + classifierId + "', id='" + instanceId + "'");
                }
            });
        }

        @Override
        protected ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds, boolean throwIfNotFound)
        {
            ListIterable<ObjOrUpdate> objOrUpdates = this.deserializer.getInstances(classifierId, instanceIds, throwIfNotFound);
            MutableList<Obj> objs = Lists.mutable.ofInitialCapacity(objOrUpdates.size());
            MutableList<ObjUpdate> objUpdates = Lists.mutable.ofInitialCapacity(0);
            objUpdates.forEach(new ObjOrUpdateConsumer()
            {
                @Override
                protected void accept(Obj obj)
                {
                    objs.add(obj);
                }

                @Override
                protected void accept(ObjUpdate objUpdate)
                {
                    objUpdates.add(objUpdate);
                }
            });
            if (objUpdates.notEmpty())
            {
                boolean many = objUpdates.size() > 1;
                StringBuilder builder = new StringBuilder("Cannot find main definition for ").append(many ? "instances: " : "instance: ");
                builder.append("classifier='").append(classifierId).append("', id");
                if (many)
                {
                    objUpdates.collect(ObjUpdate::getIdentifier).sortThis().appendString(builder, "s='", "', '", "'");
                }
                else
                {
                    builder.append("='").append(objUpdates.get(0).getIdentifier()).append("'");
                }
                throw new RuntimeException(builder.toString());
            }
            return objs;
        }
    }

    private static class Many extends MultiDistributedBinaryGraphDeserializer
    {
        private final ImmutableList<DistributedBinaryGraphDeserializer> deserializers;

        private Many(ImmutableList<DistributedBinaryGraphDeserializer> deserializers)
        {
            this.deserializers = deserializers;
        }

        @Override
        public boolean hasClassifier(String classifierId)
        {
            return this.deserializers.anySatisfy(d -> d.hasClassifier(classifierId));
        }

        @Override
        public RichIterable<String> getClassifiers()
        {
            return this.deserializers.flatCollect(DistributedBinaryGraphDeserializer::getClassifiers, Sets.mutable.empty());
        }

        @Override
        public boolean hasInstance(String classifierId, String instanceId)
        {
            return this.deserializers.anySatisfy(d -> d.hasInstance(classifierId, instanceId));
        }

        @Override
        public RichIterable<String> getClassifierInstanceIds(String classifierId)
        {
            return this.deserializers.flatCollect(d -> d.getClassifierInstanceIds(classifierId), Sets.mutable.empty());
        }

        @Override
        protected Obj getInstance(String classifierId, String instanceId, boolean throwIfNotFound)
        {
            MutableList<Obj> main = Lists.mutable.withInitialCapacity(1);
            MutableList<ObjUpdate> updates = Lists.mutable.withInitialCapacity(this.deserializers.size() - 1);
            this.deserializers.asLazy()
                    .collect(d -> d.getInstanceIfPresent(classifierId, instanceId))
                    .select(Objects::nonNull)
                    .forEach(new ObjOrUpdateConsumer()
                    {
                        @Override
                        protected void accept(Obj obj)
                        {
                            main.add(obj);
                        }

                        @Override
                        protected void accept(ObjUpdate objUpdate)
                        {
                            updates.add(objUpdate);
                        }
                    });
            if (main.isEmpty())
            {
                if (updates.notEmpty())
                {
                    // We have updates but no main definition
                    throw new RuntimeException("Cannot find main definition for instance: classifier='" + classifierId + "', id='" + instanceId + "'");
                }
                if (throwIfNotFound)
                {
                    // No updates or main definition
                    throw new RuntimeException("Unknown instance: classifier='" + classifierId + "', id='" + instanceId + "'");
                }
                return null;
            }
            if (main.size() > 1)
            {
                throw new RuntimeException("Multiple (" + main.size() + ") main definitions for instance: classifier='" + classifierId + "', id='" + instanceId + "'");
            }
            Obj obj = main.get(0);
            return updates.isEmpty() ? obj : obj.applyUpdates(updates);
        }

        @Override
        protected ListIterable<Obj> getInstances(String classifierId, Iterable<String> instanceIds, boolean throwIfNotFound)
        {
            Set<String> instanceIdSet = (instanceIds instanceof Set) ? (Set<String>) instanceIds : Sets.mutable.withAll(instanceIds);
            if (instanceIdSet.isEmpty())
            {
                return Lists.immutable.empty();
            }

            MutableMap<String, List<ObjOrUpdate>> objsById = Maps.mutable.withInitialCapacity(instanceIdSet.size());
            this.deserializers.asLazy().flatCollect(d -> d.getInstancesIfPresent(classifierId, instanceIdSet)).forEach(o -> objsById.getIfAbsentPut(o.getIdentifier(), Lists.mutable::empty).add(o));
            if (throwIfNotFound && (instanceIdSet.size() > objsById.size()))
            {
                boolean many = (instanceIdSet.size() - objsById.size()) > 1;
                StringBuilder builder = new StringBuilder(many ? "Unknown instances: " : "Unknown instance: ");
                builder.append("classifier='").append(classifierId).append("', id");
                if (many)
                {
                    Iterate.reject(instanceIdSet, objsById::containsKey, Lists.mutable.empty()).sortThis().appendString(builder, "s='", "', '", "'");
                }
                else
                {
                    builder.append("='").append(Iterate.detect(instanceIdSet, id -> !objsById.containsKey(id))).append("'");
                }
                throw new RuntimeException(builder.toString());
            }
            return objsById.valuesView().collect(this::reduceToObj, Lists.mutable.withInitialCapacity(objsById.size()));
        }

        private Obj reduceToObj(List<ObjOrUpdate> objs)
        {
            if (objs.isEmpty())
            {
                throw new IllegalArgumentException("objs may not be empty");
            }

            if (objs.size() == 1)
            {
                return objs.get(0).visit(new ObjOrUpdateVisitor<Obj>()
                {
                    @Override
                    public Obj visit(Obj obj)
                    {
                        return obj;
                    }

                    @Override
                    public Obj visit(ObjUpdate objUpdate)
                    {
                        throw new RuntimeException("Cannot find main definition for instance: classifier='" + objUpdate.getClassifier() + "', id='" + objUpdate.getIdentifier() + "'");
                    }
                });
            }

            List<Obj> main = Lists.mutable.withInitialCapacity(1);
            List<ObjUpdate> updates = Lists.mutable.withInitialCapacity(objs.size());
            objs.forEach(new ObjOrUpdateConsumer()
            {
                @Override
                protected void accept(Obj obj)
                {
                    main.add(obj);
                }

                @Override
                protected void accept(ObjUpdate objUpdate)
                {
                    updates.add(objUpdate);
                }
            });
            if (main.isEmpty())
            {
                throw new RuntimeException("Cannot find main definition for instance: classifier='" + objs.get(0).getClassifier() + "', id='" + objs.get(0).getIdentifier() + "'");
            }
            if (main.size() > 1)
            {
                throw new RuntimeException("Multiple (" + main.size() + ") main definitions for instance: classifier='" + objs.get(0).getClassifier() + "', id='" + objs.get(0).getIdentifier() + "'");
            }
            return main.get(0).applyUpdates(updates);
        }
    }
}

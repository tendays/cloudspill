package org.gamboni.cloudspill.domain;

import org.gamboni.cloudspill.shared.query.QueryRange;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.SingularAttribute;

/**
 * @author tendays
 */
public abstract class CloudSpillEntityManagerDomain {
    protected final EntityManager session;
    public final CriteriaBuilder criteriaBuilder;

    protected CloudSpillEntityManagerDomain(EntityManager session) {
        this.session = session;
        this.criteriaBuilder = session.getCriteriaBuilder();
    }

    public abstract CloudSpillEntityManagerDomain.Query<? extends BackendItem> selectItem();

    public Query<User> selectUser() {
        return new Query<>(User.class);
    }

    public abstract class QueryNode<R, SELF> {
        protected final Class<R> entityClass;
        protected final CriteriaQuery<?> criteria;
        protected final List<Function<Root<R>, Predicate>> whereClause = new ArrayList<>();
        protected QueryNode(CriteriaQuery<?> criteria, Class<R> entityClass) {
            this.criteria = criteria;
            this.entityClass = entityClass;
        }

        public SELF add(Function<Root<R>, Predicate> pred) {
            whereClause.add(pred);
            return self();
        }
        /*
                public Subquery subquery(String path, String alias) {
                    return new Subquery(criteria.createCriteria(path, alias));
                }

                public String alias(String path, String alias) {
                    criteria.createAlias(path, alias);
                    return alias;
                }
        */
        protected abstract SELF self();
    }
    /*
        public class Subquery extends QueryNode<Subquery> {
            protected Subquery(CriteriaQuery<?> criteria) {
                super(criteria);
            }

            protected Subquery self() {
                return this;
            }
        }
    */

    private interface ComparableAttributeConsumer<T> {
        <V extends Comparable<? super V>> void accept(SingularAttribute<? super T, V> attribute);
    }

    public static class Ordering<T> {
        private final SingularAttribute<? super T, ?> attribute;
        // Just for fun: this double Consumer allows having well-typed "existential types" without using casts.
        private final Consumer<ComparableAttributeConsumer<? extends T>> eAttribute;
        private final boolean ascending;

        private <V extends Comparable<? super V>> Ordering(SingularAttribute<? super T, V> attribute, boolean ascending) {
            this.attribute = attribute;
            this.eAttribute = consumer -> consumer.accept(attribute);
            this.ascending = ascending;
        }

        public static <T, V extends Comparable<? super V>> Ordering<T> asc(SingularAttribute<T, V> attribute) {
            return new Ordering<>(attribute, true);
        }

        public static <T, V extends Comparable<? super V>> Ordering<T> desc(SingularAttribute<? super T, V> attribute) {
            return new Ordering<>(attribute, false);
        }

        private void getAttribute(ComparableAttributeConsumer<? extends T> consumer) {
            this.eAttribute.accept(consumer);
        }

        public Order on(CriteriaBuilder criteriaBuilder, Root<? extends T> root) {
            return ascending ?
                            criteriaBuilder.asc(root.get(attribute)) :
                            criteriaBuilder.desc(root.get(attribute));
        }
    }

    public class Query<T> extends QueryNode<T, Query<T>> {
        private final CriteriaQuery<T> typedQuery;
        private QueryRange range = QueryRange.ALL;
        private LockModeType lockMode = null;
        private List<Ordering<? super T>> orders = new ArrayList<>();

        public Query(Class<T> persistentClass) {
            this(persistentClass, session.getCriteriaBuilder().createQuery(persistentClass));
        }

        public Query(Class<T> persistentClass, CriteriaQuery<T> cq) {
            super(cq, persistentClass);
            this.typedQuery = cq;
        }

        public Query<T> addOrder(Ordering<? super T> order) {
            this.orders.add(order);
            return this;
        }
        /*
                public Subquery join(String join) {
                    return new Subquery(criteria.createCriteria(join));
                }
        */
        public Query<T> forUpdate() {
            // 1. pessimistic locking to make concurrent clients queue instead of failing
            // 2. force-increment to ensure the entity is sent to client when they synchronise
            this.lockMode = LockModeType.PESSIMISTIC_FORCE_INCREMENT;
            return this;
        }

        /** Set or unset the maximum number of rows.
         *
         * @return this
         */
        public Query<T> range(QueryRange range) {
            this.range = range;
            return this;
        }

        public List<T> list() {
            final Root<T> root = typedQuery.from(this.entityClass);

            typedQuery.where(this.whereClause.stream().map(f -> f.apply(root)).toArray(Predicate[]::new));
            typedQuery.orderBy(this.orders.stream().map(f -> f.on(criteriaBuilder, root)).toArray(Order[]::new));
            TypedQuery<T> typedQuery = session.createQuery(this.typedQuery)
                    .setFirstResult(range.offset);

            if (range.limit != null) {
                typedQuery = typedQuery.setMaxResults(range.limit);
            }

            if (lockMode != null) {
                typedQuery = typedQuery.setLockMode(lockMode);
            }
            return typedQuery.getResultList();
        }

        @Override
        protected Query<T> self() {
            return this;
        }

        public CriteriaBuilder getCriteriaBuilder() {
            return criteriaBuilder;
        }

        public long getTotalCount() {
            final CriteriaQuery<Long> totalQuery = session.getCriteriaBuilder().createQuery(Long.class);
            final Root<T> root = totalQuery.from(this.entityClass);

            totalQuery.where(this.whereClause.stream().map(f -> f.apply(root)).toArray(Predicate[]::new));
            return session.createQuery(totalQuery
                    .select(session.getCriteriaBuilder().count(root)))
                    .getSingleResult();
        }

        public int indexOf(Item element) {
            /*
             less than first ordering, or equal to first, and less than second one, etc
             */
            this.add(root -> {
                List<Predicate> disjunction = new ArrayList<>();
                for (int i = 0; i < orders.size(); i++) {
                    List<Predicate> conjunction = new ArrayList<>();
                    for (int j = 0; j < i; j++) {
                        final SingularAttribute<? super T, ?> attribute = orders.get(j).attribute;
                        conjunction.add(getCriteriaBuilder().equal(root.get(attribute), getAttributeValue(element, attribute)));
                    }
                    final Ordering<? super T> order = orders.get(i);
                    order.getAttribute(new ComparableAttributeConsumer<T>() {
                                           @Override
                                           public <V extends Comparable<? super V>> void accept(SingularAttribute<? super T, V> attribute) {

                                               final Path<V> expression = root.get(attribute);
                                               final V value = getAttributeValue(element, attribute);

                                               conjunction.add(order.ascending ?
                                                       getCriteriaBuilder().lessThan(expression, value) :
                                                       getCriteriaBuilder().greaterThan(expression, value));
                                           }
                                       }
                    );

                    disjunction.add(getCriteriaBuilder().and(conjunction.toArray(new Predicate[0])));
                }
                return getCriteriaBuilder().or(disjunction.toArray(new Predicate[0]));
            });

            return (int)this.getTotalCount();
        }

        private <V> V getAttributeValue(Item item, SingularAttribute<? super T, V> attribute) {
            try {
                // Class.cast(x) looks nice but doesn't work with primitives
                return (V)/*attribute.getBindableJavaType().cast*/(((Method) attribute.getJavaMember()).invoke(item));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException();
            }
        }
    }

    public <T> T get(Class<T> persistentClass, Object id) {
        return session.find(persistentClass, id);
    }

    public void persist(Object entity) {
        session.persist(entity);
    }

    public void remove(Object entity) { session.remove(entity); }

    public void merge(Object entity) {
        session.merge(entity);
    }

    public void reload(Object entity) {
        session.refresh(entity);
    }

    public void flush() {
        session.flush();
    }

    public EntityManager getEntityManager() {
        return session;
    }
}

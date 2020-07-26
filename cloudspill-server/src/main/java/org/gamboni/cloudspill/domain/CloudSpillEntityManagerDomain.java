package org.gamboni.cloudspill.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

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
    public class Query<T> extends QueryNode<T, Query<T>> {
        private final CriteriaQuery<T> typedQuery;
        private int offset = 0;
        private Integer limit = null;
        private LockModeType lockMode = null;
        private List<Function<Root<T>, Order>> orders = new ArrayList<>();

        public Query(Class<T> persistentClass) {
            this(persistentClass, session.getCriteriaBuilder().createQuery(persistentClass));
        }

        public Query(Class<T> persistentClass, CriteriaQuery<T> cq) {
            super(cq, persistentClass);
            this.typedQuery = cq;
        }

        public Query<T> addOrder(Function<Root<T>, Order> order) {
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

        public Query<T> offset(int offset) {
            this.offset = offset;
            return this;
        }

        /** Set or unset the maximum number of rows.
         *
         * @param limit null to remove a previously set limit, or a number to set the number of rows.
         * @return this
         */
        public Query<T> limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public List<T> list() {
            final Root<T> root = typedQuery.from(this.entityClass);

            typedQuery.where(this.whereClause.stream().map(f -> f.apply(root)).toArray(Predicate[]::new));
            typedQuery.orderBy(this.orders.stream().map(f -> f.apply(root)).toArray(Order[]::new));
            TypedQuery<T> typedQuery = session.createQuery(this.typedQuery)
                    .setFirstResult(offset);

            if (limit != null) {
                typedQuery = typedQuery.setMaxResults(limit);
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
    }

    public <T> T get(Class<T> persistentClass, Object id) {
        return session.find(persistentClass, id);
    }

    public void persist(Object entity) {
        session.persist(entity);
    }

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

/**
 * 
 */
package org.gamboni.cloudspill.domain;

import java.util.List;

import org.hibernate.LockMode;
import org.hibernate.criterion.Projections;

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
 *
 */
public class Domain {
	private final EntityManager session;
	public final CriteriaBuilder criteriaBuilder;

	public Domain(EntityManager session) {
		this.session = session;
		this.criteriaBuilder = session.getCriteriaBuilder();
	}

	public Query<Item> selectItem() {
		return new Query<>(Item.class);
	}
	
	public Query<User> selectUser() {
		return new Query<>(User.class);
	}

	public abstract class QueryNode<SELF> {
		protected final CriteriaQuery<?> criteria;
		protected final Root<?> root;
		protected QueryNode(CriteriaQuery<?> criteria, Root<?> root) {
			this.criteria = criteria;
			this.root = root;
		}

		public SELF add(Predicate c) {
			criteria.where(c);
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
	public class Query<T> extends QueryNode<Query<T>> {
	private final CriteriaQuery<T> typedQuery;
	private int offset = 0;
	private Integer limit = null;
	private LockModeType lockMode = null;

	public Query(Class<T> persistentClass) {
			this(persistentClass, session.getCriteriaBuilder().createQuery(persistentClass));
		}

		private Query(Class<T> persistentClass, CriteriaQuery<T> cq) {
			this(cq, cq.from(persistentClass));
		}

		private Query(CriteriaQuery<T> cq, Root<T> root) {
			super(cq, root);
			cq.select(root);
			this.typedQuery = cq;
		}

		public Query<T> addOrder(Order order) {
			criteria.orderBy(order);
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

		public Query<T> limit(int limit) {
			this.limit = limit;
			return this;
		}
		
		@SuppressWarnings("unchecked")
		public List<T> list() {
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

		public long getTotalCount() {
			return session.createQuery(session.getCriteriaBuilder().createQuery(Long.class)
					.where(criteria.getRestriction())
					.select(session.getCriteriaBuilder().count(root)))
					.getSingleResult();
		}
	}

	public <T> T get(Class<T> persistentClass, long id) {
		return (T) session.find(persistentClass, id);
	}

	public void persist(Object entity) {
		session.persist(entity);
	}
	
	public void reload(Object entity) {
		session.refresh(entity);
	}
	
	public void flush() {
		session.flush();
	}
}

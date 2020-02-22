/**
 * 
 */
package org.gamboni.cloudspill.domain;

import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;

/**
 * @author tendays
 *
 */
public class Domain {
	private final Session session;

	public Domain(Session session) {
		this.session = session;
	}

	public Query<Item> selectItem() {
		return new Query<>(Item.class);
	}
	
	public Query<User> selectUser() {
		return new Query<>(User.class);
	}

	public abstract class QueryNode<SELF> {
		protected final Criteria criteria;
		protected QueryNode(Criteria criteria) {
			this.criteria = criteria;
		}

		public SELF add(Criterion c) {
			criteria.add(c);
			return self();
		}

		public String alias(String path, String alias) {
			criteria.createAlias(path, alias);
			return alias;
		}

		protected abstract SELF self();
	}

	public class Subquery extends QueryNode<Subquery> {
		protected Subquery(Criteria criteria) {
			super(criteria);
		}

		protected Subquery self() {
			return this;
		}
	}

	public class Query<T> extends QueryNode<Query<T>> {
		public Query(Class<T> persistentClass) {
			super(session.createCriteria(persistentClass));
		}

		public Query<T> addOrder(Order order) {
			criteria.addOrder(order);
			return this;
		}

		public Subquery join(String join) {
			return new Subquery(criteria.createCriteria(join));
		}
		
		public Query<T> forUpdate() {
			// 1. pessimistic locking to make concurrent clients queue instead of failing
			// 2. force-increment to ensure the entity is sent to client when they synchronise
			criteria.setLockMode(LockMode.PESSIMISTIC_FORCE_INCREMENT);
			return this;
		}

		public Query<T> offset(int offset) {
			criteria.setFirstResult(offset);
			return this;
		}

		public Query<T> limit(int limit) {
			criteria.setMaxResults(limit);
			return this;
		}
		
		@SuppressWarnings("unchecked")
		public List<T> list() {
			return criteria.list();
		}

		@Override
		protected Query<T> self() {
			return this;
		}
	}

	public <T> T get(Class<T> persistentClass, long id) {
		return (T) session.get(persistentClass, id);
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

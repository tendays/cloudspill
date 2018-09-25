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

	public class Query<T> {
		private final Criteria criteria;
		public Query(Class<T> persistentClass) {
			this.criteria = session.createCriteria(persistentClass);
		}
		
		public Query<T> add(Criterion c) {
			criteria.add(c);
			return this;
		}

		public Query<T> addOrder(Order order) {
			criteria.addOrder(order);
			return this;
		}
		
		public Query<T> forUpdate() {
			// 1. pessimistic locking to make concurrent clients queue instead of failing
			// 2. force-increment to ensure the entity is sent to client when they synchronise
			criteria.setLockMode(LockMode.PESSIMISTIC_FORCE_INCREMENT);
			return this;
		}
		
		@SuppressWarnings("unchecked")
		public List<T> list() {
			return criteria.list();
		}
	}

	public <T> T get(Class<T> persistentClass, long id) {
		return (T) session.get(persistentClass, id);
	}

	public void persist(Object entity) {
		session.persist(entity);
	}
	
	public void flush() {
		session.flush();
	}
}

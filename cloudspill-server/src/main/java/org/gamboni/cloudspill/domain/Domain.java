/**
 * 
 */
package org.gamboni.cloudspill.domain;

import java.util.List;

import org.hibernate.Criteria;
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
		
		@SuppressWarnings("unchecked")
		public List<T> list() {
			return criteria.list();
		}
	}

	@SuppressWarnings("unchecked")
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

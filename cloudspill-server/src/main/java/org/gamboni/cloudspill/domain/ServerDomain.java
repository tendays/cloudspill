/**
 * 
 */
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
 *
 */
public class ServerDomain extends CloudSpillEntityManagerDomain {

	public ServerDomain(EntityManager session) {
		super(session);
	}

	@Override
	public Query<Item> selectItem() {
		return new CloudSpillEntityManagerDomain.Query<>(Item.class);
	}

	public Query<GalleryPart> selectGalleryPart() {
		return new Query<>(GalleryPart.class);
	}

	public Query<UserAuthToken> selectUserAuthToken() {
		return new Query<>(UserAuthToken.class);
	}

}

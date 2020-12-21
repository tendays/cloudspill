package org.gamboni.cloudspill.shared.api;

import java.util.List;

/** Companion class to ItemCredentials (Needed because Android does not allow static methods in interfaces).
 *
 * @author tendays
 */
public abstract class ItemSecurity {
    public static ItemCredentials mostPowerful(List<ItemCredentials> credentials) {
        ItemCredentials mostPowerful = null;
        //credentials.stream().max(Comparator.comparing(ItemCredentials::getPower));
        for (ItemCredentials element : credentials) {
            if (mostPowerful == null || mostPowerful.getPower().compareTo(element.getPower()) < 0) {
                mostPowerful = element;
            }
        }
        return mostPowerful;
    }
}

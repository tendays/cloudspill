package org.gamboni.cloudspill.shared.api;

import org.gamboni.cloudspill.shared.domain.IsItem;
import org.gamboni.cloudspill.shared.domain.Items;

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

    public static ItemCredentials getItemCredentials(IsItem item, ItemCredentials.AuthenticationStatus authStatus) {
        ItemCredentials credentials;
        if (Items.isPublic(item)) {
            credentials = new ItemCredentials.PublicAccess();
        } else {
            if (authStatus == ItemCredentials.AuthenticationStatus.LOGGED_IN) {
                credentials = new ItemCredentials.UserPassword();
            } else {
                credentials = new ItemCredentials.ItemKey(item.getChecksum());
            }
        }
        return credentials;
    }
}

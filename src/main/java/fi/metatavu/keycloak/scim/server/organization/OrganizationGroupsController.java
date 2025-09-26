package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.groups.GroupsController;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.GroupsList;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.List;

public class OrganizationGroupsController extends GroupsController {

    public Group createOrganizationGroup(OrganizationScimContext scimContext, Group scimGroup) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        GroupModel parentGroup = session.groups().getGroupsStream(realm)
            .filter(g -> g.getFirstAttribute("organization").equals(scimContext.getOrganization().getAlias()))
            .findFirst()
            .orElse(null);

        if (parentGroup == null) {
            parentGroup = session.groups().createGroup(realm, scimContext.getOrganization().getAlias());
            parentGroup.setAttribute("organization", List.of(scimContext.getOrganization().getAlias()));
        }

        GroupModel group = session.groups().createGroup(realm, scimGroup.getDisplayName());
        parentGroup.addChild(group);

        if (scimGroup.getMembers() != null) {
            for (fi.metatavu.keycloak.scim.server.model.GroupMembersInner member : scimGroup.getMembers()) {
                UserModel user = session.users().getUserById(realm, member.getValue());
                if (user != null) {
                    user.joinGroup(group);
                }
            }
        }

        return translateGroup(scimContext, group);
    }

    public GroupsList listOrganizationGroups(OrganizationScimContext scimContext, int startIndex, int count) {
        return listGroups(scimContext, startIndex, count);
    }
}

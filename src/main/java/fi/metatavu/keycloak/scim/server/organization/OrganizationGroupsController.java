package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.filter.ComparisonFilter;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.groups.GroupsController;
import fi.metatavu.keycloak.scim.server.metadata.GroupAttribute;
import fi.metatavu.keycloak.scim.server.metadata.UserAttribute;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.GroupsList;
import fi.metatavu.keycloak.scim.server.users.UnsupportedUserPath;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class OrganizationGroupsController extends GroupsController {

    public Group createOrganizationGroup(OrganizationScimContext scimContext, Group scimGroup) {
        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        GroupModel parentGroup = session.groups().getGroupsStream(realm)
            .filter(g -> g.getFirstAttribute("organization").equals(scimContext.getOrganization().getAlias()))
            .filter(g -> !"true".equals(g.getFirstAttribute("SCIM_SKIP")))
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

    public GroupsList listOrganizationGroups(OrganizationScimContext scimContext, ScimFilter scimFilter, int startIndex, int count) {

        KeycloakSession session = scimContext.getSession();
        RealmModel realm = scimContext.getRealm();

        GroupModel parentGroup = session.groups().getGroupsStream(realm)
            .filter(g -> g.getFirstAttribute("organization").equals(scimContext.getOrganization().getAlias()))
            .findFirst()
            .orElse(null);

        List<GroupModel> allGroups = parentGroup != null ? parentGroup.getSubGroupsStream().toList() : List.of();

        List<Group> groups = allGroups.stream()
            .filter(group -> {
                if (scimFilter instanceof ComparisonFilter cmp) {
                    if (cmp.operator() == ScimFilter.Operator.EQ && GroupAttribute.DISPLAY_NAME.getScimPath().equals(cmp.attribute())) {
                        return group.getName() != null && cmp.value() != null && group.getName().equals(cmp.value());
                    }
                }
                return false;
            })
            .skip(startIndex)
            .limit(count)
            .map(group -> translateGroup(scimContext, group))
            .collect(Collectors.toList());

        GroupsList result = new GroupsList();
        result.setTotalResults(allGroups.size());
        result.setStartIndex(startIndex);
        result.setItemsPerPage(count);
        result.setResources(groups);
        result.setSchemas(Collections.singletonList("urn:ietf:params:scim:api:messages:2.0:ListResponse"));

        return result;
    }
}

package fi.metatavu.keycloak.scim.server.organization;

import fi.metatavu.keycloak.scim.server.AbstractScimServer;
import fi.metatavu.keycloak.scim.server.config.ConfigurationError;
import fi.metatavu.keycloak.scim.server.filter.ScimFilter;
import fi.metatavu.keycloak.scim.server.groups.UnsupportedGroupPath;
import fi.metatavu.keycloak.scim.server.jacoco.ExcludeFromJacocoGeneratedReport;
import fi.metatavu.keycloak.scim.server.metadata.UserAttributes;
import fi.metatavu.keycloak.scim.server.model.Group;
import fi.metatavu.keycloak.scim.server.model.PatchRequest;
import fi.metatavu.keycloak.scim.server.model.User;
import fi.metatavu.keycloak.scim.server.patch.UnsupportedPatchOperation;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import org.jboss.logging.Logger;
import org.keycloak.models.*;

import java.net.URI;
import java.util.Objects;

/**
 * SCIM server implementation for organizations
 */
public class OrganizationScimServer extends AbstractScimServer<OrganizationScimContext> {

    private static final Logger logger = Logger.getLogger(OrganizationScimServer.class);
    private final OrganizationController organizationController;
    private final OrganizationUserController organizationUserController;
    private final OrganizationGroupsController organizationGroupsController;

    public OrganizationScimServer() {
        this.organizationController = new OrganizationController();
        this.organizationUserController = new OrganizationUserController();
        this.organizationGroupsController = new OrganizationGroupsController();
    }

    @Override
    public Response createUser(OrganizationScimContext scimContext, User createRequest) {
        boolean emailAsUsername = scimContext.getConfig().getEmailAsUsername();

        if (isBlank(createRequest.getUserName())) {
            logger.warn("Cannot create user: Missing userName");
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing userName").build();
        }

        if (emailAsUsername && !isValidEmail(createRequest.getUserName())) {
            logger.warn("Cannot create user: Invalid email format for userName");
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid email format for userName").build();
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        User user = organizationUserController.createOrganizationUser(
            scimContext,
            userAttributes,
            createRequest
        );

        URI location = scimContext.getServerBaseUri().resolve(String.format("v2/Users/%s", user.getId()));

        return Response
            .created(location)
            .entity(user)
            .build();
    }

    @Override
    public Response updateUser(OrganizationScimContext scimContext, String userId, fi.metatavu.keycloak.scim.server.model.User updateRequest) {
        boolean emailAsUsername = scimContext.getConfig().getEmailAsUsername();
        KeycloakSession session = scimContext.getSession();
        String username = updateRequest.getUserName();

        if (isBlank(username)) {
            logger.warn("Missing userName");
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing userName").build();
        }

        if (emailAsUsername && !isValidEmail(updateRequest.getUserName())) {
            logger.warn("Cannot update user: Invalid email format for userName");
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid email format for userName").build();
        }

        if (emailAsUsername && updateRequest.getEmails() != null) {
            for (fi.metatavu.keycloak.scim.server.model.UserEmailsInner email : updateRequest.getEmails()) {
                if (!Objects.equals(email.getValue(), updateRequest.getUserName())) {
                    logger.warn("Conflicting email and userName when emailAsUsername is enabled");
                    return Response.status(Response.Status.BAD_REQUEST).entity("Username and email must match when emailAsUsername is enabled").build();
                }
            }
        }

        RealmModel realm = scimContext.getRealm();
        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        // Check if username is being changed to an already existing one
        UserModel existing;
        if (emailAsUsername) {
            existing = session.users().getUserByEmail(realm, updateRequest.getUserName());
        } else {
            existing = session.users().getUserByUsername(realm, updateRequest.getUserName());
        }

        if (existing != null && !existing.getId().equals(userId)) {
            logger.warn(String.format("User name already taken: %s", updateRequest.getUserName()));
            return Response.status(Response.Status.CONFLICT).entity("User name already taken").build();
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);
        fi.metatavu.keycloak.scim.server.model.User result = organizationUserController.updateOrganizationUser(scimContext, userAttributes, user, updateRequest);

        return Response.ok(result).build();
    }

    @Override
    public Response patchUser(OrganizationScimContext scimContext, String userId, fi.metatavu.keycloak.scim.server.model.PatchRequest patchRequest) {
        KeycloakSession session = scimContext.getSession();

        RealmModel realm = scimContext.getRealm();
        UserModel existing = session.users().getUserById(realm, userId);
        if (existing == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
        }

        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        try {
            fi.metatavu.keycloak.scim.server.model.User result = organizationUserController.patchOrganizationUser(scimContext, userAttributes, existing, patchRequest);
            return Response.ok(result).build();
        } catch (UnsupportedPatchOperation e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported patch operation").build();
        }
    }

    @Override
    public Response listUsers(OrganizationScimContext scimContext, ScimFilter scimFilter, Integer startIndex, Integer count) {
        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);

        fi.metatavu.keycloak.scim.server.model.UsersList usersList = organizationUserController.listOrganizationUsers(
            scimContext,
            scimFilter,
            userAttributes,
            startIndex,
            count
        );

        return Response.ok(usersList).build();
    }

    @Override
    public Response findUser(OrganizationScimContext scimContext, String userId) {
        UserAttributes userAttributes = metadataController.getUserAttributes(scimContext);
        User user = organizationUserController.findOrganizationUser(scimContext, userAttributes, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(user).build();
    }

    @Override
    public Response deleteUser(OrganizationScimContext scimContext, String userId) {
        RealmModel realm = scimContext.getRealm();
        KeycloakSession session = scimContext.getSession();

        UserModel user = session.users().getUserById(realm, userId);
        if (user == null) {
            logger.warn(String.format("User not found: %s", userId));
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        RoleModel scimManagedRole = realm.getRole("scim-managed");
        if (scimManagedRole != null && !user.hasRole(scimManagedRole)) {
            logger.warn(String.format("User is not SCIM-managed: %s", userId));
            return Response.status(Response.Status.FORBIDDEN).entity("User is not managed by SCIM").build();
        }

        organizationUserController.deleteOrganizationUser(scimContext, user);

        return Response.noContent().build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response createGroup(OrganizationScimContext scimContext, Group createRequest) {
        fi.metatavu.keycloak.scim.server.model.Group created = organizationGroupsController.createOrganizationGroup(scimContext, createRequest);
        URI location = UriBuilder.fromPath("v2/organizations/{organizationId}/Groups/{id}").build(scimContext.getOrganization().getId(), created.getId());
        return Response
            .created(location)
            .entity(created)
            .build();
    }

    @ExcludeFromJacocoGeneratedReport
    public Response listGroups(OrganizationScimContext scimContext, ScimFilter scimFilter, int startIndex, int count) {
        fi.metatavu.keycloak.scim.server.model.GroupsList groupList = organizationGroupsController.listOrganizationGroups(scimContext, scimFilter, startIndex, count);
        return Response.ok(groupList).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response findGroup(OrganizationScimContext scimContext, String id) {
        fi.metatavu.keycloak.scim.server.model.Group group = groupsController.findGroup(scimContext, id);
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(group).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response updateGroup(OrganizationScimContext scimContext, String id, Group updateRequest) {
        KeycloakSession session = scimContext.getSession();

        GroupModel existing = session.groups().getGroupById(scimContext.getRealm(), id);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!id.equals(existing.getId())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Group ID mismatch").build();
        }

        fi.metatavu.keycloak.scim.server.model.Group updated = groupsController.updateGroup(
            scimContext,
            existing,
            updateRequest
        );

        return Response.ok(updated).build();
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response patchGroup(OrganizationScimContext scimContext, String groupId, PatchRequest patchRequest) {
        KeycloakSession session = scimContext.getSession();

        GroupModel existing = session.groups().getGroupById(scimContext.getRealm(), groupId);
        if (existing == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!groupId.equals(existing.getId())) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Group ID mismatch").build();
        }

        try {
            fi.metatavu.keycloak.scim.server.model.Group updated = groupsController.patchGroup(scimContext, existing, patchRequest);
            return Response.ok(updated).build();
        } catch (UnsupportedGroupPath e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported group path").build();
        } catch (UnsupportedPatchOperation e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Unsupported patch operation").build();
        }
    }

    @Override
    @ExcludeFromJacocoGeneratedReport
    public Response deleteGroup(OrganizationScimContext scimContext, String id) {
        KeycloakSession session = scimContext.getSession();
        GroupModel group = session.groups().getGroupById(scimContext.getRealm(), id);
        if (group == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        groupsController.deleteGroup(scimContext, group);

        return Response.noContent().build();
    }

    /**
     * Returns SCIM context
     *
     * @param session Keycloak session
     * @return SCIM context
     */
    public OrganizationScimContext getScimContext(KeycloakSession session, String organizationId) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            throw new NotFoundException("Realm not found");
        }

        OrganizationModel organization = organizationController.findOrganizationById(
            session,
            organizationId
        );

        if (organization == null) {
            throw new NotFoundException("Organization not found");
        }

        KeycloakContext context = session.getContext();
        context.setOrganization(organization);

        URI baseUri = session.getContext().getUri().getBaseUri().resolve(String.format("realms/%s/scim/v2/organizations/%s/", realm.getName(), organization.getId()));
        OrganizationScimConfig config = new OrganizationScimConfig(organization);

        try {
            config.validateConfig();
        } catch (ConfigurationError e) {
            throw new InternalServerErrorException("Invalid SCIM configuration", e);
        }

        return new OrganizationScimContext(
            baseUri,
            session,
            realm,
            organization,
            config
        );
    }

}

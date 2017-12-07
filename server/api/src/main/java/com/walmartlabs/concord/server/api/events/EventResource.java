package com.walmartlabs.concord.server.api.events;

import io.swagger.annotations.Api;
import io.swagger.annotations.Authorization;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

@Api(value = "Events", authorizations = {@Authorization("api_key"), @Authorization("ldap")})
@Path("/api/v1/events")
public interface EventResource {

    @POST
    @Path("/{eventName:.*}")
    @Consumes(MediaType.APPLICATION_JSON)
    Response event(@PathParam("eventName") String eventName, Map<String, Object> event);
}

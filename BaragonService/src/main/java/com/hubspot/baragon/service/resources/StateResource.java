package com.hubspot.baragon.service.resources;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Optional;
import com.google.common.net.HttpHeaders;
import com.google.inject.Inject;
import com.hubspot.baragon.auth.NoAuth;
import com.hubspot.baragon.cache.BaragonStateCache;
import com.hubspot.baragon.cache.CachedBaragonState;
import com.hubspot.baragon.models.AgentResponse;
import com.hubspot.baragon.models.BaragonResponse;
import com.hubspot.baragon.models.BaragonServiceState;
import com.hubspot.baragon.service.managers.AgentManager;
import com.hubspot.baragon.service.managers.RequestManager;
import com.hubspot.baragon.service.managers.ServiceManager;

@Path("/state")
@Produces(MediaType.APPLICATION_JSON)
public class StateResource {
  private final ServiceManager serviceManager;
  private final BaragonStateCache stateCache;
  private final AgentManager agentManager;


  @Inject
  public StateResource(ServiceManager serviceManager, BaragonStateCache stateCache, AgentManager agentManager) {
    this.serviceManager = serviceManager;
    this.stateCache = stateCache;
    this.agentManager = agentManager;
  }

  @GET
  @NoAuth
  @Timed
  public Response getAllServices(@HeaderParam(HttpHeaders.IF_NONE_MATCH) String ifNoneMatch,
                                 @HeaderParam(HttpHeaders.ACCEPT_ENCODING) String acceptEncoding) {
    CachedBaragonState state = stateCache.getState();
    String verisonString = Integer.toString(state.getVersion());

    if (ifNoneMatch != null && ifNoneMatch.equals(verisonString.trim())) {
      return Response.notModified()
          .header(HttpHeaders.ETAG, state.getVersion())
          .build();
    }

    ResponseBuilder builder = Response.ok();

    final byte[] entity;
    if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
      builder.header(HttpHeaders.CONTENT_ENCODING, "gzip");
      entity = state.getGzip();
    } else {
      entity = state.getUncompressed();
    }

    return builder
        .entity(entity)
        .header(HttpHeaders.ETAG, state.getVersion())
        .build();
  }

  @GET
  @NoAuth
  @Path("/{serviceId}")
  public Optional<BaragonServiceState> getService(@PathParam("serviceId") String serviceId) {
    return serviceManager.getService(serviceId);
  }

  @POST
  @NoAuth
  @Path("/{serviceId}/renderConfigs")
  public BaragonResponse renderConfigs(@PathParam("serviceId") String serviceId) {
    return serviceManager.enqueueRenderedConfigs(serviceId);
  }

  @GET
  @NoAuth
  @Path("/{serviceId}/renderConfigs")
  public Optional<AgentResponse> renderConfigsGET(@PathParam("serviceId") String serviceId) throws Exception {
    return agentManager.synchronouslySendRenderConfigsRequest(serviceId);
  }

  @POST
  @Path("/{serviceId}/reload")
  public BaragonResponse reloadConfigs(@PathParam("serviceId") String serviceId, @DefaultValue("false") @QueryParam("noValidate") boolean noValidate) {
    return serviceManager.enqueueReloadServiceConfigs(serviceId, noValidate);
  }

  @DELETE
  @Path("/{serviceId}")
  public BaragonResponse removeService(@PathParam("serviceId") String serviceId, @DefaultValue("false") @QueryParam("noValidate") boolean noValidate, @DefaultValue("false") @QueryParam("noReload") boolean noReload) {
    return serviceManager.enqueueRemoveService(serviceId, noValidate, noReload);
  }
}

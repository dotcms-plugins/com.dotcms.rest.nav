package com.dotcms.plugin.rest;

import com.dotcms.repackage.javax.ws.rs.GET;
import com.dotcms.repackage.javax.ws.rs.Path;
import com.dotcms.repackage.javax.ws.rs.PathParam;
import com.dotcms.repackage.javax.ws.rs.Produces;
import com.dotcms.repackage.javax.ws.rs.QueryParam;
import com.dotcms.repackage.javax.ws.rs.core.Context;
import com.dotcms.repackage.javax.ws.rs.core.MediaType;
import com.dotcms.repackage.javax.ws.rs.core.Response;
import com.dotcms.rest.InitDataObject;
import com.dotcms.rest.WebResource;
import com.dotcms.rest.exception.mapper.ExceptionMapperUtil;

import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.business.PermissionLevel;
import com.dotmarketing.business.web.WebAPILocator;
import com.dotmarketing.exception.DotDataException;
import com.dotmarketing.exception.DotRuntimeException;
import com.dotmarketing.exception.DotSecurityException;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.VelocityUtil;
import com.dotmarketing.viewtools.navigation.NavResult;
import com.dotmarketing.viewtools.navigation.NavTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.tools.view.context.ChainedContext;
import org.apache.velocity.tools.view.context.ViewContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liferay.portal.model.User;

@Path("/v1/nav")
public class NavResource  {

    private final WebResource webResource = new WebResource();

    @Produces({MediaType.APPLICATION_JSON, "application/javascript"})
	@GET
	@Path("/{uri: .*}")
    public Response loadJson(@Context final HttpServletRequest request, @Context final HttpServletResponse response,
            @PathParam("uri") final String uri, @QueryParam("depth") final int depth) {
        System.err.println("NavResource hit");
        final InitDataObject auth = webResource.init(false, request, true);
        final User user = auth.getUser();

        Response res = null;
        try {
            Host h =WebAPILocator.getHostWebAPI().getCurrentHost(request);
            APILocator.getPermissionAPI().checkPermission(h, PermissionLevel.READ, user);
            
            ViewContext ctx = new ChainedContext(VelocityUtil.getBasicContext(), request, response, Config.CONTEXT);

            final String path = (!uri.startsWith("/")) ? "/" + uri : uri;

            NavTool tool = new NavTool();
            tool.init(ctx);
            NavResult nav = tool.getNav(path);
            
            Map<String, Object> navMap = (nav!=null) ? navToMap(nav, depth, 1) : new HashMap<>();

            final Response.ResponseBuilder responseBuilder = Response.ok(navMap);

            responseBuilder.header("Access-Control-Expose-Headers", "Authorization");
            responseBuilder.header("Access-Control-Allow-Headers",
                    "Origin, X-Requested-With, " + "Content-Type, " + "Accept, Authorization");


            res = responseBuilder.build();
        } catch (JsonProcessingException e) {
            final String errorMsg = "An error occurred when generating the JSON response (" + e.getMessage() + ")";
            Logger.error(this, e.getMessage(), e);
            res = ExceptionMapperUtil.createResponse(null, errorMsg);
        } catch (DotSecurityException e) {
            final String errorMsg = "The user does not have the required permissions (" + e.getMessage() + ")";
            Logger.error(this, errorMsg, e);
            throw new DotRuntimeException(e);
        } catch (DotDataException e) {
            final String errorMsg = "An error occurred when accessing the page information (" + e.getMessage() + ")";
            Logger.error(this, e.getMessage(), e);
            res = ExceptionMapperUtil.createResponse(null, errorMsg);
        } catch (Exception e) {
            final String errorMsg = "An internal error occurred (" + e.getMessage() + ")";
            Logger.error(this, errorMsg, e);
            res = ExceptionMapperUtil.createResponse(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
        return res;
	}    
	private Map<String, Object> navToMap(final NavResult nav, final int maxDepth, final int currentDepth) throws Exception {

        Map<String, Object> navMap = new HashMap<String, Object>();
        navMap.put("title", nav.getTitle());
        navMap.put("target", nav.getTarget());
        navMap.put("code", nav.getCodeLink());
        navMap.put("host", nav.getHostId());
        navMap.put("href", nav.getHref());
        navMap.put("order", nav.getOrder());
        navMap.put("type", nav.getType());
        navMap.put("hash", nav.hashCode());
        
        if (currentDepth < maxDepth) {
            List<Map<String, Object>> childs = new ArrayList<>();
            for (NavResult child : nav.getChildren()) {
                int startDepth=currentDepth;
                childs.add(navToMap(child, maxDepth, ++startDepth));
            }
            navMap.put("children", childs);
        }

        return navMap;
    }
}
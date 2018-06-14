/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.webadmin.routes;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static org.apache.james.webadmin.Constants.EMPTY_BODY;
import static org.apache.james.webadmin.Constants.JSON_CONTENT_TYPE;
import static org.apache.james.webadmin.Constants.SEPARATOR;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.core.Domain;
import org.apache.james.dlp.api.DLPConfigurationItem;
import org.apache.james.dlp.api.DLPConfigurationStore;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.dto.DLPConfigurationDTO;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.ErrorResponder.ErrorType;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;

import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.github.steveash.guavate.Guavate;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.HaltException;
import spark.Request;
import spark.Service;

@Api(tags = "DLPRules")
@Path(DLPConfigurationRoutes.BASE_PATH)
@Produces(JSON_CONTENT_TYPE)
public class DLPConfigurationRoutes implements Routes {

    static final String BASE_PATH = "/dlp/rules";

    private static final String DOMAIN_NAME = ":senderDomain";
    private static final String SPECIFIC_DLP_RULE_DOMAIN = BASE_PATH + SEPARATOR + DOMAIN_NAME;

    private final JsonTransformer jsonTransformer;
    private final DLPConfigurationStore dlpConfigurationStore;
    private final JsonExtractor<DLPConfigurationDTO> jsonExtractor;
    private final DomainList domainList;

    private Service service;

    @Inject
    public DLPConfigurationRoutes(DLPConfigurationStore dlpConfigurationStore, DomainList domainList, JsonTransformer jsonTransformer) {
        this.dlpConfigurationStore = dlpConfigurationStore;
        this.domainList = domainList;
        this.jsonTransformer = jsonTransformer;
        this.jsonExtractor = new JsonExtractor<>(DLPConfigurationDTO.class, new GuavaModule());
    }

    @Override
    public void define(Service service) {
        this.service = service;

        defineStore();

        defineList();

        defineClear();
    }

    @PUT
    @Path("/{senderDomain}")
    @ApiOperation(value = "Store a list of dlp configs for given senderDomain")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "senderDomain", paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. dlp config is stored."),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid senderDomain or payload in request"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The domain does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public void defineStore() {
        service.put(SPECIFIC_DLP_RULE_DOMAIN, (request, response) -> {
            Domain senderDomain = parseDomain(request);
            DLPConfigurationDTO dto = jsonExtractor.parse(request.body());

            dlpConfigurationStore.store(senderDomain, DLPConfigurationDTO.toDLPConfigurations(dto));

            response.status(HttpStatus.NO_CONTENT_204);
            return EMPTY_BODY;
        });
    }

    @GET
    @Path("/{senderDomain}")
    @ApiOperation(value = "Retrieve a list of dlp configs for given senderDomain")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "senderDomain", paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.OK_200, message = "OK. dlp configs returned"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid senderDomain in request"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The domain does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public void defineList() {
        service.get(SPECIFIC_DLP_RULE_DOMAIN, (request, response) -> {
            Domain senderDomain = parseDomain(request);
            List<DLPConfigurationItem> dlpConfigurations = dlpConfigurationStore
                .list(senderDomain)
                .collect(Guavate.toImmutableList());

            DLPConfigurationDTO dto = DLPConfigurationDTO.toDTO(dlpConfigurations);
            response.status(HttpStatus.OK_200);
            response.header(CONTENT_TYPE, JSON_CONTENT_TYPE);
            return dto;
        }, jsonTransformer);
    }

    @DELETE
    @Path("/{senderDomain}")
    @ApiOperation(value = "Clear all dlp configs for given senderDomain")
    @ApiImplicitParams({
        @ApiImplicitParam(required = true, dataType = "string", name = "senderDomain", paramType = "path")
    })
    @ApiResponses(value = {
        @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. dlp configs are cleared"),
        @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid senderDomain in request"),
        @ApiResponse(code = HttpStatus.NOT_FOUND_404, message = "The domain does not exist."),
        @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
            message = "Internal server error - Something went bad on the server side.")
    })
    public void defineClear() {
        service.delete(SPECIFIC_DLP_RULE_DOMAIN, (request, response) -> {
            Domain senderDomain = parseDomain(request);
            dlpConfigurationStore.clear(senderDomain);

            response.status(HttpStatus.NO_CONTENT_204);
            return EMPTY_BODY;
        }, jsonTransformer);
    }

    private Domain parseDomain(Request request) {
        String domainName = request.params(DOMAIN_NAME);
        try {
            Domain domain = Domain.of(domainName);
            validateDomainInList(domain);

            return domain;
        } catch (IllegalArgumentException e) {
            throw invalidDomain(String.format("Invalid request for domain: %s", domainName), e);
        } catch (DomainListException e) {
            throw serverError(String.format("Cannot recognize domain: %s in domain list", domainName), e);
        }
    }

    private void validateDomainInList(Domain domain) throws DomainListException {
        if (!domainList.containsDomain(domain)) {
            throw notFound(String.format("'%s' is not managed by this James server", domain.name()));
        }
    }

    private HaltException invalidDomain(String message, Exception e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .type(ErrorType.INVALID_ARGUMENT)
            .message(message)
            .cause(e)
            .haltError();
    }

    private HaltException serverError(String message, Exception e) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.INTERNAL_SERVER_ERROR_500)
            .type(ErrorType.SERVER_ERROR)
            .message(message)
            .cause(e)
            .haltError();
    }

    private HaltException notFound(String message) {
        return ErrorResponder.builder()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .type(ErrorType.INVALID_ARGUMENT)
            .message(message)
            .haltError();
    }
}
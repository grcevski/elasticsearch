/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.security.action.rolemapping;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.reservedstate.ReservedClusterStateHandler;
import org.elasticsearch.reservedstate.TransformState;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xcontent.XContentParserConfiguration;
import org.elasticsearch.xpack.core.security.action.rolemapping.DeleteRoleMappingRequest;
import org.elasticsearch.xpack.core.security.action.rolemapping.PutRoleMappingRequest;
import org.elasticsearch.xpack.core.security.authc.support.mapper.ExpressionRoleMapping;
import org.elasticsearch.xpack.security.authc.support.mapper.NativeRoleMappingStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.elasticsearch.common.xcontent.XContentHelper.mapToXContentParser;

/**
 * This Action is the reserved state save version of RestPutRoleMappingAction/RestDeleteRoleMappingAction
 * <p>
 * It is used by the ReservedClusterStateService to add/update or remove role mappings. Typical usage
 * for this action is in the context of file based settings.
 */
public class ReservedRoleMappingAction implements ReservedClusterStateHandler<List<ExpressionRoleMapping>> {
    public static final String NAME = "role_mappings";

    private final NativeRoleMappingStore roleMappingStore;

    /**
     * Creates a ReservedRoleMappingAction
     *
     * @param roleMappingStore requires {@link NativeRoleMappingStore} for storing/deleting the mappings
     */
    public ReservedRoleMappingAction(NativeRoleMappingStore roleMappingStore) {
        this.roleMappingStore = roleMappingStore;
    }

    @Override
    public String name() {
        return NAME;
    }

    @SuppressWarnings("unchecked")
    public Collection<PutRoleMappingRequest> prepare(Object input) {
        List<ExpressionRoleMapping> roleMappings = (List<ExpressionRoleMapping>) input;
        List<PutRoleMappingRequest> requests = roleMappings.stream().map(rm -> PutRoleMappingRequest.fromMapping(rm)).toList();

        for (var request : requests) {
            var exception = request.validate();
            if (exception != null) {
                throw exception;
            }
        }

        return requests;
    }

    @Override
    public TransformState transform(Object source, TransformState prevState) throws Exception {
        var requests = prepare(source);

        ClusterState state = prevState.state();

        for (var request : requests) {
            roleMappingStore.putRoleMapping(
                request,
                new ActionListener<>() {
                    @Override
                    public void onResponse(Boolean aBoolean) {}

                    @Override
                    public void onFailure(Exception e) {
                        throw new IllegalStateException("Error creating role mapping [" + request.getName() + "]", e);
                    }
                }
            );
        }

        Set<String> entities = requests.stream().map(r -> r.getName()).collect(Collectors.toSet());

        Set<String> toDelete = new HashSet<>(prevState.keys());
        toDelete.removeAll(entities);

        for (var mappingToDelete : toDelete) {
            var deleteRequest = new DeleteRoleMappingRequest();
            deleteRequest.setName(mappingToDelete);

            roleMappingStore.deleteRoleMapping(
                deleteRequest,
                new ActionListener<>() {
                    @Override
                    public void onResponse(Boolean aBoolean) {}

                    @Override
                    public void onFailure(Exception e) {
                        throw new IllegalStateException("Error deleting role mapping [" + mappingToDelete + "]", e);
                    }
                }
            );
        }

        return new TransformState(state, entities);
    }

    @Override
    public List<ExpressionRoleMapping> fromXContent(XContentParser parser) throws IOException {
        List<ExpressionRoleMapping> result = new ArrayList<>();

        Map<String, ?> source = parser.map();

        for (String name : source.keySet()) {
            @SuppressWarnings("unchecked")
            Map<String, ?> content = (Map<String, ?>) source.get(name);
            try (XContentParser mappingParser = mapToXContentParser(XContentParserConfiguration.EMPTY, content)) {
                ExpressionRoleMapping mapping = ExpressionRoleMapping.parse(name, mappingParser);
                result.add(mapping);
            }
        }

        return result;
    }
}

/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.cluster.metadata;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.cluster.Diff;
import org.elasticsearch.cluster.DiffableUtils;
import org.elasticsearch.cluster.SimpleDiffable;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xcontent.ToXContent;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OperatorMetadata implements SimpleDiffable<OperatorMetadata> {
    private final String namespace;
    private final Long version;
    private final Map<String, OperatorHandlerMetadata> handlers;
    private final OperatorErrorMetadata errorMetadata;

    public OperatorMetadata(
        String namespace,
        Long version,
        Map<String, OperatorHandlerMetadata> handlers,
        OperatorErrorMetadata errorMetadata
    ) {
        this.namespace = namespace;
        this.version = version;
        this.handlers = handlers;
        this.errorMetadata = errorMetadata;
    }

    public String namespace() {
        return namespace;
    }

    public Long version() {
        return version;
    }

    public OperatorErrorMetadata errorMetadata() {
        return errorMetadata;
    }

    public Map<String, OperatorHandlerMetadata> handlers() {
        return handlers;
    }

    public static OperatorMetadata readFrom(StreamInput in) throws IOException {
        Builder builder = new Builder(in.readString()).version(in.readLong());

        int handlersSize = in.readVInt();
        for (int i = 0; i < handlersSize; i++) {
            OperatorHandlerMetadata handler = OperatorHandlerMetadata.readFrom(in);
            builder.putHandler(handler);
        }

        builder.errorMetadata(in.readOptionalWriteable(OperatorErrorMetadata::readFrom));
        return builder.build();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(namespace);
        out.writeLong(version);
        out.writeCollection(handlers.values());
        out.writeOptionalWriteable(errorMetadata);
    }

    public static Diff<OperatorMetadata> readDiffFrom(StreamInput in) throws IOException {
        return SimpleDiffable.readDiffFrom(OperatorMetadata::readFrom, in);
    }

    public static final DiffableUtils.MapDiff<String, OperatorMetadata, Map<String, OperatorMetadata>> EMPTY_DIFF =
        new DiffableUtils.MapDiff<>(null, null, List.of(), List.of(), List.of()) {
            @Override
            public Map<String, OperatorMetadata> apply(Map<String, OperatorMetadata> part) {
                return part;
            }
        };

    public static Builder builder(String namespace) {
        return new Builder(namespace);
    }

    public static Builder builder(String namespace, OperatorMetadata metadata) {
        return new Builder(namespace, metadata);
    }

    public static class Builder {
        private static final String VERSION = "version";
        private static final String HANDLERS = "handlers";
        private static final String ERRORS_METADATA = "errors";

        private final String namespace;
        private Long version;
        private Map<String, OperatorHandlerMetadata> handlers;
        OperatorErrorMetadata errorMetadata;

        public Builder(String namespace) {
            this.namespace = namespace;
            this.version = 0L;
            this.handlers = new HashMap<>();
            this.errorMetadata = null;
        }

        public Builder(String namespace, OperatorMetadata metadata) {
            this(namespace);
            if (metadata != null) {
                this.version = metadata.version;
                this.handlers = metadata.handlers;
                this.errorMetadata = metadata.errorMetadata;
            }
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
        }

        public Builder errorMetadata(OperatorErrorMetadata errorMetadata) {
            this.errorMetadata = errorMetadata;
            return this;
        }

        public Builder handlerKeys(Map<String, OperatorHandlerMetadata> handlers) {
            this.handlers = handlers;
            return this;
        }

        public Builder putHandler(OperatorHandlerMetadata handler) {
            this.handlers.put(handler.name(), handler);
            return this;
        }

        public OperatorMetadata build() {
            return new OperatorMetadata(namespace, version, Collections.unmodifiableMap(handlers), errorMetadata);
        }

        /**
         * Serializes the metadata to xContent
         *
         * @param operatorMetadata
         * @param builder
         * @param params
         */
        public static void toXContent(OperatorMetadata operatorMetadata, XContentBuilder builder, ToXContent.Params params)
            throws IOException {
            builder.startObject(operatorMetadata.namespace());
            builder.field(VERSION, operatorMetadata.version());
            builder.startObject(HANDLERS);
            for (OperatorHandlerMetadata handlerMetadata : operatorMetadata.handlers().values()) {
                OperatorHandlerMetadata.Builder.toXContent(handlerMetadata, builder, params);
            }
            builder.endObject();
            builder.field(ERRORS_METADATA, operatorMetadata.errorMetadata);
            builder.endObject();
        }

        /**
         * Reads the metadata from xContent
         *
         * @param parser
         * @param namespace
         * @return
         * @throws IOException
         */
        public static OperatorMetadata fromXContent(XContentParser parser, String namespace) throws IOException {
            OperatorMetadata.Builder builder = new OperatorMetadata.Builder(namespace);

            String currentFieldName = parser.currentName();
            XContentParser.Token token;
            while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                if (token == XContentParser.Token.FIELD_NAME) {
                    currentFieldName = parser.currentName();
                } else if (token == XContentParser.Token.START_OBJECT) {
                    if (HANDLERS.equals(currentFieldName)) {
                        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                            builder.putHandler(OperatorHandlerMetadata.Builder.fromXContent(parser));
                        }
                    } else if (ERRORS_METADATA.equals(currentFieldName)) {
                        builder.errorMetadata(OperatorErrorMetadata.Builder.fromXContent(parser));
                    } else {
                        throw new ElasticsearchParseException("unknown key [{}] for operator metadata", currentFieldName);
                    }
                } else if (token.isValue()) {
                    if (VERSION.equals(currentFieldName)) {
                        builder.version(parser.longValue());
                    }
                }
            }
            return builder.build();
        }
    }
}

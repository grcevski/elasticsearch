/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */
package org.elasticsearch.xpack.deprecation;

import org.elasticsearch.action.ingest.PutPipelineRequest;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.ComponentTemplate;
import org.elasticsearch.cluster.metadata.IndexTemplateMetadata;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.metadata.Template;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.compress.CompressedXContent;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.mapper.FieldNamesFieldMapper;
import org.elasticsearch.ingest.IngestService;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.xpack.core.deprecation.DeprecationIssue;
import org.elasticsearch.xpack.core.ilm.IndexLifecycleMetadata;
import org.elasticsearch.xpack.core.ilm.LifecycleAction;
import org.elasticsearch.xpack.core.ilm.LifecyclePolicy;
import org.elasticsearch.xpack.core.ilm.LifecyclePolicyMetadata;
import org.elasticsearch.xpack.core.ilm.OperationMode;
import org.elasticsearch.xpack.core.ilm.Phase;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.elasticsearch.cluster.routing.allocation.DiskThresholdSettings.CLUSTER_ROUTING_ALLOCATION_INCLUDE_RELOCATIONS_SETTING;
import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.xpack.core.ilm.LifecycleSettings.LIFECYCLE_POLL_INTERVAL_SETTING;
import static org.elasticsearch.xpack.deprecation.DeprecationChecks.CLUSTER_SETTINGS_CHECKS;
import static org.elasticsearch.xpack.deprecation.IndexDeprecationChecksTests.addRandomFields;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

public class ClusterDeprecationChecksTests extends ESTestCase {

    public void testUserAgentEcsCheck() {
        PutPipelineRequest ecsFalseRequest = new PutPipelineRequest(
            "ecs_false",
            new BytesArray(
                "{\n"
                    + "  \"description\" : \"This has ecs set to false\",\n"
                    + "  \"processors\" : [\n"
                    + "    {\n"
                    + "      \"user_agent\" : {\n"
                    + "        \"field\" : \"agent\",\n"
                    + "        \"ecs\" : false\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}"
            ),
            XContentType.JSON
        );
        PutPipelineRequest ecsNullRequest = new PutPipelineRequest(
            "ecs_null",
            new BytesArray(
                "{\n"
                    + "  \"description\" : \"This has ecs set to false\",\n"
                    + "  \"processors\" : [\n"
                    + "    {\n"
                    + "      \"user_agent\" : {\n"
                    + "        \"field\" : \"agent\"\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}"
            ),
            XContentType.JSON
        );
        PutPipelineRequest ecsTrueRequest = new PutPipelineRequest(
            "ecs_true",
            new BytesArray(
                "{\n"
                    + "  \"description\" : \"This has ecs set to false\",\n"
                    + "  \"processors\" : [\n"
                    + "    {\n"
                    + "      \"user_agent\" : {\n"
                    + "        \"field\" : \"agent\",\n"
                    + "        \"ecs\" : true\n"
                    + "      }\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}"
            ),
            XContentType.JSON
        );

        ClusterState state = ClusterState.builder(new ClusterName("test")).build();
        state = IngestService.innerPut(ecsTrueRequest, state);
        state = IngestService.innerPut(ecsFalseRequest, state);
        state = IngestService.innerPut(ecsNullRequest, state);

        final ClusterState finalState = state;
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(CLUSTER_SETTINGS_CHECKS, c -> c.apply(finalState));

        DeprecationIssue expected = new DeprecationIssue(
            DeprecationIssue.Level.WARNING,
            "The User-Agent ingest processor's ecs parameter is deprecated",
            "https://ela.st/es-deprecation-7-ingest-pipeline-ecs-option",
            "Remove the ecs parameter from your ingest pipelines. The User-Agent ingest processor always returns Elastic Common Schema "
                + "(ECS) fields in 8.0.",
            false,
            null
        );
        assertEquals(singletonList(expected), issues);
    }

    public void testTemplateWithTooManyFields() throws IOException {
        String tooManyFieldsTemplate = randomAlphaOfLength(5);
        String tooManyFieldsWithDefaultFieldsTemplate = randomAlphaOfLength(6);
        String goodTemplateName = randomAlphaOfLength(7);

        // A template with too many fields
        int tooHighFieldCount = randomIntBetween(1025, 10_000); // 10_000 is arbitrary
        XContentBuilder badMappingBuilder = jsonBuilder();
        badMappingBuilder.startObject();
        {
            badMappingBuilder.startObject("_doc");
            {
                badMappingBuilder.startObject("properties");
                {
                    addRandomFields(tooHighFieldCount, badMappingBuilder);
                }
                badMappingBuilder.endObject();
            }
            badMappingBuilder.endObject();
        }
        badMappingBuilder.endObject();

        // A template with an OK number of fields
        int okFieldCount = randomIntBetween(1, 1024);
        XContentBuilder goodMappingBuilder = jsonBuilder();
        goodMappingBuilder.startObject();
        {
            goodMappingBuilder.startObject("_doc");
            {
                goodMappingBuilder.startObject("properties");
                {
                    addRandomFields(okFieldCount, goodMappingBuilder);
                }
                goodMappingBuilder.endObject();
            }
            goodMappingBuilder.endObject();
        }
        goodMappingBuilder.endObject();

        final ClusterState state = ClusterState.builder(new ClusterName(randomAlphaOfLength(5)))
            .metadata(
                Metadata.builder()
                    .put(
                        IndexTemplateMetadata.builder(tooManyFieldsTemplate)
                            .patterns(Collections.singletonList(randomAlphaOfLength(5)))
                            .putMapping("_doc", Strings.toString(badMappingBuilder))
                            .build()
                    )
                    .put(
                        IndexTemplateMetadata.builder(tooManyFieldsWithDefaultFieldsTemplate)
                            .patterns(Collections.singletonList(randomAlphaOfLength(5)))
                            .putMapping("_doc", Strings.toString(badMappingBuilder))
                            .settings(
                                Settings.builder()
                                    .put(
                                        IndexSettings.DEFAULT_FIELD_SETTING.getKey(),
                                        Collections.singletonList(randomAlphaOfLength(5)).toString()
                                    )
                            )
                            .build()
                    )
                    .put(
                        IndexTemplateMetadata.builder(goodTemplateName)
                            .patterns(Collections.singletonList(randomAlphaOfLength(5)))
                            .putMapping("_doc", Strings.toString(goodMappingBuilder))
                            .build()
                    )
                    .build()
            )
            .build();

        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(CLUSTER_SETTINGS_CHECKS, c -> c.apply(state));

        DeprecationIssue expected = new DeprecationIssue(
            DeprecationIssue.Level.WARNING,
            "Fields in index template exceed automatic field expansion limit",
            "https://ela.st/es-deprecation-7-number-of-auto-expanded-fields",
            "Index templates "
                + Collections.singletonList(tooManyFieldsTemplate)
                + " have a number of fields which exceeds the "
                + "automatic field expansion limit of [1024] and does not have ["
                + IndexSettings.DEFAULT_FIELD_SETTING.getKey()
                + "] set, "
                + "which may cause queries which use automatic field expansion, such as query_string, simple_query_string, and multi_match "
                + "to fail if fields are not explicitly specified in the query.",
            false,
            null
        );
        assertEquals(singletonList(expected), issues);
    }

    public void testTemplatesWithFieldNamesDisabled() throws IOException {
        XContentBuilder goodMappingBuilder = jsonBuilder();
        goodMappingBuilder.startObject();
        {
            goodMappingBuilder.startObject("_doc");
            {
                goodMappingBuilder.startObject("properties");
                {
                    addRandomFields(10, goodMappingBuilder);
                }
                goodMappingBuilder.endObject();
            }
            goodMappingBuilder.endObject();
        }
        goodMappingBuilder.endObject();
        assertFieldNamesEnabledTemplate(goodMappingBuilder, false);

        XContentBuilder badMappingBuilder = jsonBuilder();
        badMappingBuilder.startObject();
        {
            // we currently always store a type level internally
            badMappingBuilder.startObject("_doc");
            {
                badMappingBuilder.startObject(FieldNamesFieldMapper.NAME);
                {
                    badMappingBuilder.field("enabled", randomBoolean());
                }
                badMappingBuilder.endObject();
            }
            badMappingBuilder.endObject();
        }
        badMappingBuilder.endObject();
        assertFieldNamesEnabledTemplate(badMappingBuilder, true);

        // however, there was a bug where mappings could be stored without a type (#45120)
        // so we also should try to check these cases

        XContentBuilder badMappingWithoutTypeBuilder = jsonBuilder();
        badMappingWithoutTypeBuilder.startObject();
        {
            badMappingWithoutTypeBuilder.startObject(FieldNamesFieldMapper.NAME);
            {
                badMappingWithoutTypeBuilder.field("enabled", randomBoolean());
            }
            badMappingWithoutTypeBuilder.endObject();
        }
        badMappingWithoutTypeBuilder.endObject();
        assertFieldNamesEnabledTemplate(badMappingWithoutTypeBuilder, true);
    }

    private void assertFieldNamesEnabledTemplate(XContentBuilder templateBuilder, boolean expectIssue) throws IOException {
        String badTemplateName = randomAlphaOfLength(5);
        final ClusterState state = ClusterState.builder(new ClusterName(randomAlphaOfLength(5)))
            .metadata(
                Metadata.builder()
                    .put(
                        IndexTemplateMetadata.builder(badTemplateName)
                            .patterns(Collections.singletonList(randomAlphaOfLength(5)))
                            .putMapping("_doc", Strings.toString(templateBuilder))
                            .build()
                    )
                    .build()
            )
            .build();

        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(CLUSTER_SETTINGS_CHECKS, c -> c.apply(state));
        if (expectIssue) {
            assertEquals(1, issues.size());
            DeprecationIssue issue = issues.get(0);
            assertEquals(DeprecationIssue.Level.WARNING, issue.getLevel());
            assertEquals("https://ela.st/es-deprecation-7-field_names-settings", issue.getUrl());
            assertEquals("Disabling the \"_field_names\" field in a template's index mappings is deprecated", issue.getMessage());
            assertEquals(
                "Remove the \"_field_names\" mapping that configures the enabled setting from the following templates: "
                    + "\""
                    + badTemplateName
                    + "\". There's no longer a need to disable this field to reduce index overhead if you have a lot "
                    + "of fields.",
                issue.getDetails()
            );
        } else {
            assertTrue(issues.isEmpty());
        }
    }

    public void testPollIntervalTooLow() {
        {
            final String tooLowInterval = randomTimeValue(1, 999, "ms", "micros", "nanos");
            Metadata badMetaDtata = Metadata.builder()
                .persistentSettings(Settings.builder().put(LIFECYCLE_POLL_INTERVAL_SETTING.getKey(), tooLowInterval).build())
                .build();
            ClusterState badState = ClusterState.builder(new ClusterName("test")).metadata(badMetaDtata).build();

            DeprecationIssue expected = new DeprecationIssue(
                DeprecationIssue.Level.CRITICAL,
                "Index Lifecycle Management poll interval is set too low",
                "https://ela.st/es-deprecation-7-indices-lifecycle-poll-interval-setting",
                "The ILM ["
                    + LIFECYCLE_POLL_INTERVAL_SETTING.getKey()
                    + "] setting is set to ["
                    + tooLowInterval
                    + "]. "
                    + "Set the interval to at least 1s.",
                false,
                null
            );
            List<DeprecationIssue> issues = DeprecationChecks.filterChecks(CLUSTER_SETTINGS_CHECKS, c -> c.apply(badState));
            assertEquals(singletonList(expected), issues);
        }

        // Test that other values are ok
        {
            final String okInterval = randomTimeValue(1, 9999, "d", "h", "s");
            Metadata okMetadata = Metadata.builder()
                .persistentSettings(Settings.builder().put(LIFECYCLE_POLL_INTERVAL_SETTING.getKey(), okInterval).build())
                .build();
            ClusterState okState = ClusterState.builder(new ClusterName("test")).metadata(okMetadata).build();
            List<DeprecationIssue> noIssues = DeprecationChecks.filterChecks(CLUSTER_SETTINGS_CHECKS, c -> c.apply(okState));
            assertThat(noIssues, hasSize(0));
        }
    }

    public void testIndexTemplatesWithMultipleTypes() throws IOException {

        IndexTemplateMetadata multipleTypes = IndexTemplateMetadata.builder("multiple-types")
            .patterns(Collections.singletonList("foo"))
            .putMapping("type1", "{\"type1\":{}}")
            .putMapping("type2", "{\"type2\":{}}")
            .build();
        IndexTemplateMetadata singleType = IndexTemplateMetadata.builder("single-type")
            .patterns(Collections.singletonList("foo"))
            .putMapping("type1", "{\"type1\":{}}")
            .build();
        ImmutableOpenMap<String, IndexTemplateMetadata> templates = ImmutableOpenMap.<String, IndexTemplateMetadata>builder()
            .fPut("multiple-types", multipleTypes)
            .fPut("single-type", singleType)
            .build();
        Metadata badMetadata = Metadata.builder().templates(templates).build();
        ClusterState badState = ClusterState.builder(new ClusterName("test")).metadata(badMetadata).build();
        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(CLUSTER_SETTINGS_CHECKS, c -> c.apply(badState));
        assertThat(issues, hasSize(1));
        assertThat(
            issues.get(0).getDetails(),
            equalTo(
                "Update or remove the following index templates before upgrading to 8.0: [multiple-types]. See "
                    + "https://ela.st/es-deprecation-7-removal-of-types for alternatives to mapping types."
            )
        );
        assertWarnings(
            "Index template multiple-types contains multiple typed mappings;" + " templates in 8x will only support a single mapping"
        );

        Metadata goodMetadata = Metadata.builder()
            .templates(ImmutableOpenMap.<String, IndexTemplateMetadata>builder().fPut("single-type", singleType).build())
            .build();
        ClusterState goodState = ClusterState.builder(new ClusterName("test")).metadata(goodMetadata).build();
        assertThat(DeprecationChecks.filterChecks(CLUSTER_SETTINGS_CHECKS, c -> c.apply(goodState)), hasSize(0));
    }

    public void testClusterRoutingAllocationIncludeRelocationsSetting() {
        boolean settingValue = randomBoolean();
        String settingKey = CLUSTER_ROUTING_ALLOCATION_INCLUDE_RELOCATIONS_SETTING.getKey();
        final Settings deprecatedSetting = Settings.builder().put(settingKey, settingValue).build();

        Metadata.Builder metadataBuilder = Metadata.builder();
        if (randomBoolean()) {
            metadataBuilder.transientSettings(deprecatedSetting);
        } else {
            metadataBuilder.persistentSettings(deprecatedSetting);
        }
        ClusterState clusterState = ClusterState.builder(new ClusterName("test"))
            .metadata(metadataBuilder.transientSettings(deprecatedSetting).build())
            .build();

        final DeprecationIssue expectedIssue = new DeprecationIssue(
            DeprecationIssue.Level.WARNING,
            String.format(Locale.ROOT, "Setting [%s] is deprecated", settingKey),
            "https://ela.st/es-deprecation-7-cluster-routing-allocation-disk-include-relocations-setting",
            String.format(Locale.ROOT, "Remove the [%s] setting. Relocating shards are always taken into account in 8.0.", settingKey),
            false,
            null
        );

        final DeprecationIssue otherExpectedIssue = new DeprecationIssue(
            DeprecationIssue.Level.WARNING,
            "Transient cluster settings are deprecated",
            "https://ela.st/es-deprecation-7-transient-cluster-settings",
            "Use persistent settings to configure your cluster.",
            false,
            null
        );

        List<DeprecationIssue> issues = DeprecationChecks.filterChecks(CLUSTER_SETTINGS_CHECKS, c -> c.apply(clusterState));

        assertThat(issues, hasSize(2));
        assertThat(issues, hasItem(expectedIssue));
        assertThat(issues, hasItem(otherExpectedIssue));

        final String expectedWarning = String.format(
            Locale.ROOT,
            "[%s] setting was deprecated in Elasticsearch and will be removed in a future release! "
                + "See the breaking changes documentation for the next major version.",
            settingKey
        );

        assertWarnings(expectedWarning);
    }

    public void testCheckGeoShapeMappings() throws Exception {
        // First, testing only an index template:
        IndexTemplateMetadata indexTemplateMetadata = IndexTemplateMetadata.builder("single-type")
            .patterns(Collections.singletonList("foo"))
            .putMapping(
                "_doc",
                "{\n"
                    + "   \"_doc\":{\n"
                    + "      \"properties\":{\n"
                    + "         \"nested_field\":{\n"
                    + "            \"type\":\"nested\",\n"
                    + "            \"properties\":{\n"
                    + "               \"location\":{\n"
                    + "                  \"type\":\"geo_shape\",\n"
                    + "                  \"strategy\":\"recursive\",\n"
                    + "                  \"points_only\":true\n"
                    + "               }\n"
                    + "            }\n"
                    + "         }\n"
                    + "      }\n"
                    + "   }\n"
                    + "}"
            )
            .build();
        ImmutableOpenMap<String, IndexTemplateMetadata> templates = ImmutableOpenMap.<String, IndexTemplateMetadata>builder()
            .fPut("single-type", indexTemplateMetadata)
            .build();
        Metadata badMetadata = Metadata.builder().templates(templates).build();
        ClusterState badState = ClusterState.builder(new ClusterName("test")).metadata(badMetadata).build();
        DeprecationIssue issue = ClusterDeprecationChecks.checkGeoShapeTemplates(badState);

        assertThat(
            issue,
            equalTo(
                new DeprecationIssue(
                    DeprecationIssue.Level.CRITICAL,
                    "[single-type] index template uses deprecated geo_shape properties",
                    "https://ela.st/es-deprecation-7-geo-shape-mappings",
                    "Remove the following deprecated geo_shape properties from the mappings: [parameter [points_only] in field [location]; "
                        + "parameter [strategy] in field [location]].",
                    false,
                    null
                )
            )
        );

        // Second, testing only a component template:
        String templateName = "my-template";
        Settings settings = Settings.builder().put("index.number_of_shards", 1).build();
        CompressedXContent mappings = new CompressedXContent(
            "{\"properties\":{\"location\":{\"type\":\"geo_shape\", " + "\"strategy\":\"recursive\", \"points_only\":true}}}"
        );
        AliasMetadata alias = AliasMetadata.builder("alias").writeIndex(true).build();
        Template template = new Template(settings, mappings, Collections.singletonMap("alias", alias));
        ComponentTemplate componentTemplate = new ComponentTemplate(template, 1L, new HashMap<>());
        badMetadata = Metadata.builder().componentTemplates(Collections.singletonMap(templateName, componentTemplate)).build();
        badState = ClusterState.builder(new ClusterName("test")).metadata(badMetadata).build();
        issue = ClusterDeprecationChecks.checkGeoShapeTemplates(badState);

        assertThat(
            issue,
            equalTo(
                new DeprecationIssue(
                    DeprecationIssue.Level.CRITICAL,
                    "[my-template] component template uses deprecated geo_shape properties",
                    "https://ela.st/es-deprecation-7-geo-shape-mappings",
                    "Remove the following deprecated geo_shape properties from the mappings: [parameter [points_only] in field [location]; "
                        + "parameter [strategy] in field [location]].",
                    false,
                    null
                )
            )
        );

        // Third, trying a component template and an index template:
        badMetadata = Metadata.builder()
            .componentTemplates(Collections.singletonMap(templateName, componentTemplate))
            .templates(templates)
            .build();
        badState = ClusterState.builder(new ClusterName("test")).metadata(badMetadata).build();
        issue = ClusterDeprecationChecks.checkGeoShapeTemplates(badState);

        assertThat(
            issue,
            equalTo(
                new DeprecationIssue(
                    DeprecationIssue.Level.CRITICAL,
                    "[my-template] component template and [single-type] index template use deprecated geo_shape properties",
                    "https://ela.st/es-deprecation-7-geo-shape-mappings",
                    "Remove the following deprecated geo_shape properties from the mappings: [my-template: [parameter [points_only] in"
                        + " field [location]; parameter [strategy] in field [location]]]; [single-type: [parameter [points_only] in field "
                        + "[location]; parameter [strategy] in field [location]]].",
                    false,
                    null
                )
            )
        );
    }

    public void testSparseVectorMappings() throws Exception {
        // First, testing only an index template:
        IndexTemplateMetadata indexTemplateMetadata = IndexTemplateMetadata.builder("single-type")
            .patterns(Collections.singletonList("foo"))
            .putMapping(
                "_doc",
                "{\n"
                    + "   \"_doc\":{\n"
                    + "      \"properties\":{\n"
                    + "         \"my_sparse_vector\":{\n"
                    + "            \"type\":\"sparse_vector\"\n"
                    + "         },\n"
                    + "         \"nested_field\":{\n"
                    + "            \"type\":\"nested\",\n"
                    + "            \"properties\":{\n"
                    + "               \"my_nested_sparse_vector\":{\n"
                    + "                  \"type\":\"sparse_vector\"\n"
                    + "               }\n"
                    + "            }\n"
                    + "         }\n"
                    + "      }\n"
                    + "   }\n"
                    + "}"
            )
            .build();
        ImmutableOpenMap<String, IndexTemplateMetadata> templates = ImmutableOpenMap.<String, IndexTemplateMetadata>builder()
            .fPut("single-type", indexTemplateMetadata)
            .build();
        Metadata badMetadata = Metadata.builder().templates(templates).build();
        ClusterState badState = ClusterState.builder(new ClusterName("test")).metadata(badMetadata).build();
        DeprecationIssue issue = ClusterDeprecationChecks.checkSparseVectorTemplates(badState);

        assertThat(
            issue,
            equalTo(
                new DeprecationIssue(
                    DeprecationIssue.Level.CRITICAL,
                    "[single-type] index template uses deprecated sparse_vector properties",
                    "https://ela.st/es-deprecation-7-sparse-vector",
                    "Remove the following deprecated sparse_vector properties from the mappings: [my_sparse_vector]; "
                        + "[my_nested_sparse_vector].",
                    false,
                    null
                )
            )
        );

        // Second, testing only a component template:
        String templateName = "my-template";
        Settings settings = Settings.builder().put("index.number_of_shards", 1).build();
        CompressedXContent mappings = new CompressedXContent("{\"properties\":{\"my_sparse_vector\":{\"type\":\"sparse_vector\"}}}");
        AliasMetadata alias = AliasMetadata.builder("alias").writeIndex(true).build();
        Template template = new Template(settings, mappings, Collections.singletonMap("alias", alias));
        ComponentTemplate componentTemplate = new ComponentTemplate(template, 1L, new HashMap<>());
        badMetadata = Metadata.builder().componentTemplates(Collections.singletonMap(templateName, componentTemplate)).build();
        badState = ClusterState.builder(new ClusterName("test")).metadata(badMetadata).build();
        issue = ClusterDeprecationChecks.checkSparseVectorTemplates(badState);

        assertThat(
            issue,
            equalTo(
                new DeprecationIssue(
                    DeprecationIssue.Level.CRITICAL,
                    "[my-template] component template uses deprecated sparse_vector properties",
                    "https://ela.st/es-deprecation-7-sparse-vector",
                    "Remove the following deprecated sparse_vector properties from the mappings: [my_sparse_vector].",
                    false,
                    null
                )
            )
        );

        // Third, trying a component template and an index template:
        badMetadata = Metadata.builder()
            .componentTemplates(Collections.singletonMap(templateName, componentTemplate))
            .templates(templates)
            .build();
        badState = ClusterState.builder(new ClusterName("test")).metadata(badMetadata).build();
        issue = ClusterDeprecationChecks.checkSparseVectorTemplates(badState);

        assertThat(
            issue,
            equalTo(
                new DeprecationIssue(
                    DeprecationIssue.Level.CRITICAL,
                    "[my-template] component template and [single-type] index template use deprecated sparse_vector properties",
                    "https://ela.st/es-deprecation-7-sparse-vector",
                    "Remove the following deprecated sparse_vector properties from the mappings: [my-template: "
                        + "[my_sparse_vector]]; [single-type: [my_sparse_vector]; [my_nested_sparse_vector]].",
                    false,
                    null
                )
            )
        );
    }

    public void testCheckILMFreezeActions() throws Exception {
        Map<String, LifecyclePolicyMetadata> policies = new HashMap<>();
        Map<String, Phase> phases1 = new HashMap<>();
        Map<String, LifecycleAction> coldActions = new HashMap<>();
        coldActions.put("freeze", null);
        Phase coldPhase = new Phase("cold", TimeValue.ZERO, coldActions);
        Phase somePhase = new Phase("somePhase", TimeValue.ZERO, null);
        phases1.put("cold", coldPhase);
        phases1.put("somePhase", somePhase);
        LifecyclePolicy policy1 = new LifecyclePolicy("policy1", phases1, null);
        LifecyclePolicyMetadata policy1Metadata = new LifecyclePolicyMetadata(policy1, null, 0, 0);
        policies.put("policy1", policy1Metadata);
        Map<String, Phase> phases2 = new HashMap<>();
        phases2.put("cold", coldPhase);
        LifecyclePolicy policy2 = new LifecyclePolicy("policy2", phases2, null);
        LifecyclePolicyMetadata policy2Metadata = new LifecyclePolicyMetadata(policy2, null, 0, 0);
        policies.put("policy2", policy2Metadata);
        Metadata.Custom lifecycle = new IndexLifecycleMetadata(policies, OperationMode.RUNNING);
        Metadata badMetadata = Metadata.builder().putCustom("index_lifecycle", lifecycle).build();
        ClusterState badState = ClusterState.builder(new ClusterName("test")).metadata(badMetadata).build();
        DeprecationIssue issue = ClusterDeprecationChecks.checkILMFreezeActions(badState);
        assertThat(
            issue,
            equalTo(
                new DeprecationIssue(
                    DeprecationIssue.Level.WARNING,
                    "ILM policies use the deprecated freeze action",
                    "https://ela.st/es-deprecation-7-frozen-indices",
                    "Remove the freeze action from ILM policies: [policy1,policy2]",
                    false,
                    null
                )
            )
        );
    }

    public void testCheckTransientSettingsExistence() {
        Settings persistentSettings = Settings.builder().put("xpack.monitoring.collection.enabled", true).build();

        Settings transientSettings = Settings.builder()
            .put("indices.recovery.max_bytes_per_sec", "20mb")
            .put("action.auto_create_index", true)
            .put("cluster.routing.allocation.enable", "primaries")
            .build();
        Metadata metadataWithTransientSettings = Metadata.builder()
            .persistentSettings(persistentSettings)
            .transientSettings(transientSettings)
            .build();

        ClusterState badState = ClusterState.builder(new ClusterName("test")).metadata(metadataWithTransientSettings).build();
        DeprecationIssue issue = ClusterDeprecationChecks.checkTransientSettingsExistence(badState);
        assertThat(
            issue,
            equalTo(
                new DeprecationIssue(
                    DeprecationIssue.Level.WARNING,
                    "Transient cluster settings are deprecated",
                    "https://ela.st/es-deprecation-7-transient-cluster-settings",
                    "Use of transient settings is deprecated. Some Elastic products "
                        + "may make use of transient settings and those should not be changed. "
                        + "Any custom use of transient settings should be replaced by persistent settings.",
                    false,
                    null
                )
            )
        );

        persistentSettings = Settings.builder().put("indices.recovery.max_bytes_per_sec", "20mb").build();
        Metadata metadataWithoutTransientSettings = Metadata.builder().persistentSettings(persistentSettings).build();

        ClusterState okState = ClusterState.builder(new ClusterName("test")).metadata(metadataWithoutTransientSettings).build();
        issue = ClusterDeprecationChecks.checkTransientSettingsExistence(okState);
        assertNull(issue);
    }
}

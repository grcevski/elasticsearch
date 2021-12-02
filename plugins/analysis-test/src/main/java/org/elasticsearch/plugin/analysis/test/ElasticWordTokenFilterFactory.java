/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugin.analysis.test;

import org.apache.lucene.analysis.FilteringTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.ESTokenStream;
import org.elasticsearch.index.analysis.PluginTokenFilterFactory;

public class ElasticWordTokenFilterFactory extends PluginTokenFilterFactory {
    public ElasticWordTokenFilterFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);
    }

    @Override
    public ESTokenStream newInstance(ESTokenStream input) {
        return wrap(new ElasticWordOnlyTokenFilter(input));
    }

    public class ElasticWordOnlyTokenFilter extends FilteringTokenFilter {
        private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
        private final ESTokenStream in;

        public ElasticWordOnlyTokenFilter(ESTokenStream in) {
            super(in);
            this.in = in;
        }

        @Override
        protected boolean accept() {
            boolean result = termAtt.toString().equalsIgnoreCase("elastic");
            return result;
        }
    }
}

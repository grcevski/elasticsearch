/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;

public abstract class PluginTokenizerFactory extends AbstractTokenizerFactory {
    public PluginTokenizerFactory(IndexSettings indexSettings, Settings settings, String name) {
        super(indexSettings, settings, name);
    }

    @Override
    public final Tokenizer create() {
        ESTokenizer pluginWrappedTokenizer = newInstance();
        return unwrap(pluginWrappedTokenizer);
    }

    private Tokenizer unwrap(ESTokenizer tokenizer) {
        return tokenizer.getDelegate();
    }

    public abstract ESTokenizer newInstance();

    public static ESTokenizer wrap(Object tokenizer) {
        return ESTokenizer.wrap(tokenizer);
    }
}

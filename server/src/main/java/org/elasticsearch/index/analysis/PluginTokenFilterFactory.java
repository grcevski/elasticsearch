/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;

public abstract class PluginTokenFilterFactory extends AbstractTokenFilterFactory {

    private final String name;

    public PluginTokenFilterFactory(IndexSettings indexSettings, String name, Settings settings) {
        super(indexSettings, name, settings);
        this.name = name;
        Analysis.checkForDeprecatedVersion(name, settings);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public final TokenStream create(TokenStream tokenStream) {
        ESTokenStream pluginWrappedTokenStream = create(new ESTokenStream(tokenStream));
        return unwrap(pluginWrappedTokenStream);
    }

    private TokenStream unwrap(ESTokenStream tokenStream) {
        return tokenStream.getDelegate();
    }

    public abstract ESTokenStream create(ESTokenStream input);

    public static ESTokenStream wrap(Object tokenStream) {
        return ESTokenStream.wrap(tokenStream);
    }
}

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

    public PluginTokenFilterFactory(IndexSettings indexSettings, String name, Settings settings) {
        super(indexSettings, name, settings);
        Analysis.checkForDeprecatedVersion(name, settings);
    }

    @Override
    public final TokenStream create(TokenStream tokenStream) {
        try {
            ESTokenStream stream = (ESTokenStream) this.getClass()
                .getClassLoader()
                .loadClass("org.elasticsearch.index.analysis.ESTokenStream")
                .getConstructor(this.getClass().getClassLoader().loadClass("org.apache.lucene.analysis.TokenStream"))
                .newInstance(tokenStream);
            return newInstance(stream);
        } catch (Throwable t) {
            System.out.println(t);
            return null;
        }
    }

    public abstract ESTokenStream newInstance(ESTokenStream input);

    public static ESTokenStream wrap(Object tokenStream) {
        return ESTokenStream.wrap(tokenStream);
    }
}

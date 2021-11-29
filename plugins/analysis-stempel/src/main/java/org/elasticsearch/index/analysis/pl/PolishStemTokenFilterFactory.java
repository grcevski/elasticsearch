/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis.pl;

import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.analysis.stempel.StempelFilter;
import org.apache.lucene.analysis.stempel.StempelStemmer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.ESTokenStream;
import org.elasticsearch.index.analysis.PluginTokenFilterFactory;

public class PolishStemTokenFilterFactory extends PluginTokenFilterFactory {

    public PolishStemTokenFilterFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);
    }

    @Override
    public ESTokenStream create(ESTokenStream tokenStream) {
        return wrap(new StempelFilter(tokenStream.unwrap(this), new StempelStemmer(PolishAnalyzer.getDefaultTable())));
    }
}

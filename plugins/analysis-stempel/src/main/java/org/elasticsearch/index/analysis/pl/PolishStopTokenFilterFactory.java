/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis.pl;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.pl.PolishAnalyzer;
import org.apache.lucene.search.suggest.analyzing.SuggestStopFilter;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.Analysis;
import org.elasticsearch.index.analysis.ESTokenStream;
import org.elasticsearch.index.analysis.PluginTokenFilterFactory;

import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonMap;

public class PolishStopTokenFilterFactory extends PluginTokenFilterFactory {
    private static final Map<String, Set<?>> NAMED_STOP_WORDS = singletonMap("_polish_", PolishAnalyzer.getDefaultStopSet());

    private final CharArraySet stopWords;

    private final boolean ignoreCase;

    private final boolean removeTrailing;

    public PolishStopTokenFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
        super(indexSettings, name, settings);
        this.ignoreCase = settings.getAsBoolean("ignore_case", false);
        this.removeTrailing = settings.getAsBoolean("remove_trailing", true);
        this.stopWords = Analysis.parseWords(env, settings, "stopwords", PolishAnalyzer.getDefaultStopSet(), NAMED_STOP_WORDS, ignoreCase);
    }

    @Override
    public ESTokenStream create(ESTokenStream stream) {
        TokenStream input = stream.unwrap(this);
        if (removeTrailing) {
            return new ESTokenStream(new StopFilter(input, stopWords));
        } else {
            return new ESTokenStream(new SuggestStopFilter(input, stopWords));
        }
    }

    public Set<?> stopWords() {
        return stopWords;
    }

    public boolean ignoreCase() {
        return ignoreCase;
    }

}

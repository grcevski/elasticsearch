/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.Tokenizer;

public class ESTokenizer {
    private final Tokenizer delegate;

    public ESTokenizer(Tokenizer in) {
        this.delegate = in;
    }

    Tokenizer getDelegate() {
        return delegate;
    }

    public Tokenizer unwrap(PluginTokenizerFactory factory) {
        return delegate;
    }

    public static ESTokenizer wrap(Object tokenizer) {
        return new ESTokenizer((Tokenizer) tokenizer);
    }
}

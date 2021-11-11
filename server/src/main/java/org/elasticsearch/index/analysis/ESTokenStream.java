/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.Attribute;

public class ESTokenStream {
    private final TokenStream delegate;

    public ESTokenStream(ESTokenStream in) {
        this.delegate = in.delegate;
    }

    public ESTokenStream(TokenStream in) {
        ensureTokenStreamForwardCompatibility(in, TokenStream.class);
        this.delegate = in;
    }

    TokenStream getDelegate() {
        return delegate;
    }

    private static void ensureClassCompatibility(Class<?> tokenStreamClass) {
        if (tokenStreamClass.getName().equals(TokenStream.class.getName()) == false) {
            throw new IllegalArgumentException("You must provide a Lucene TokenStream.class instance");
        }
    }

    public Object unwrap(Class<?> tokenStreamClass, Class<?>... attributeClasses) {
        ensureClassCompatibility(tokenStreamClass);
        if (attributeClasses != null) {
            for (int i = 0; i < attributeClasses.length; i++) {
                delegate.addAttribute((Class<? extends Attribute>)attributeClasses[i]);
            }
        }

        // If the TokenStream interface ever breaks in the future we'll need to use
        // reflection or method handles to ensure we retain binary compatibility with
        // the plugins MIN lucene version.
        ensureTokenStreamBackwardCompatibility(delegate, tokenStreamClass);
        return delegate;
    }

    private void ensureTokenStreamBackwardCompatibility(Object stream, Class<?> oldVersion) {
        // NOP for now
    }

    private void ensureTokenStreamForwardCompatibility(Object stream, Class<?> newVersion) {
        // NOP for now
    }

    public static ESTokenStream wrap(Object tokenStream) {
        ensureClassCompatibility(tokenStream.getClass());
        return new ESTokenStream((TokenStream) tokenStream);
    }
}

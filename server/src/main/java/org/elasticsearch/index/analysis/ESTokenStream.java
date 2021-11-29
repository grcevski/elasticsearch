/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;

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

    private static boolean instanceOrSubclass(Class<?> clazz, Class<?> match) {
        if (match.isAssignableFrom(clazz)) {
            return true;
        }

        String matchingClassName = match.getCanonicalName();

        while (Object.class.equals(clazz) == false) {
            if (clazz.getCanonicalName().equals(matchingClassName)) {
                return true;
            }

            Class<?> superClass = clazz.getSuperclass();
            if (superClass == null) {
                break;
            }
            clazz = superClass;
        }

        return false;
    }

    private static void ensureClassCompatibility(Class<?> tokenStreamClass) {
        if (instanceOrSubclass(tokenStreamClass, TokenStream.class) == false) {
            throw new IllegalArgumentException("You must provide a Lucene TokenStream.class instance");
        }
    }

    public TokenStream unwrap(PluginTokenFilterFactory factory) {
        Class<?> tokenStreamClass;
        try {
            tokenStreamClass = factory.getClass().getClassLoader().loadClass("org.apache.lucene.analysis.TokenStream");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("You must bring Lucene core and analysis with your plugin as a dependency.");
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

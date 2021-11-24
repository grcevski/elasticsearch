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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ESTokenStream {
    private final TokenStream delegate;
    private final List<Class<? extends Attribute>> pluginUsedAttributes = new ArrayList<>();

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

    public Object unwrap(Class<?> tokenStreamClass) {
        return unwrap(tokenStreamClass, Collections.emptyList());
    }

    public Object unwrap(Class<?> tokenStreamClass, List<Class<? extends Attribute>> attributeClasses) {
        ensureClassCompatibility(tokenStreamClass);
        if (attributeClasses != null) {
            for (Class<? extends Attribute> attrClass : attributeClasses) {
                delegate.addAttribute(attrClass);
                pluginUsedAttributes.add(attrClass);
            }
        }

        // If the TokenStream interface ever breaks in the future we'll need to use
        // reflection or method handles to ensure we retain binary compatibility with
        // the plugins MIN lucene version.
        ensureTokenStreamBackwardCompatibility(delegate, tokenStreamClass);
        return delegate;
    }

    public List<Class<? extends Attribute>> getPluginUsedAttributes() {
        return pluginUsedAttributes;
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

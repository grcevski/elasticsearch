/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Attribute;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ESTokenStream extends TokenStream {
    private final Object delegate;
    private final MethodHandle mhEnd;
    private final MethodHandle mhReset;
    private final MethodHandle mhClose;
    private final MethodHandle mhHashcode;
    private final MethodHandle mhEquals;
    private final MethodHandle mhToString;
    private final MethodHandle mhIncrementToken;
    private final MethodHandle mhAddAttribute;

    private final Object termAtt;

    public ESTokenStream(Object in) {
        super((TokenStream)in);
        StableAnalysisPluginAPI.ensureClassCompatibility(in.getClass(), "org.apache.lucene.analysis.TokenStream");
        this.delegate = in;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            mhEnd = lookup.findVirtual(in.getClass(), "end", MethodType.methodType(void.class));
            mhReset = lookup.findVirtual(in.getClass(), "reset", MethodType.methodType(void.class));
            mhClose = lookup.findVirtual(in.getClass(), "close", MethodType.methodType(void.class));
            mhHashcode = lookup.findVirtual(in.getClass(), "hashCode", MethodType.methodType(int.class));
            mhEquals = lookup.findVirtual(in.getClass(), "equals", MethodType.methodType(boolean.class, Object.class));
            mhToString = lookup.findVirtual(in.getClass(), "toString", MethodType.methodType(String.class));
            mhIncrementToken = lookup.findVirtual(
                in.getClass(), "incrementToken", MethodType.methodType(boolean.class));
            mhAddAttribute = lookup.findVirtual(
                in.getClass(), "addAttribute", MethodType.methodType(Attribute.class, Class.class));

            termAtt = mhAddAttribute.invoke(delegate, CharTermAttribute.class);

        } catch (Throwable x) {
            throw new IllegalArgumentException("Incompatible Lucene library provided", x);
        }
    }

    public ESTokenStream unwrap(PluginTokenFilterFactory factory) {

        return this;
    }

    public static ESTokenStream wrap(Object tokenStream) {
        return new ESTokenStream(tokenStream);
    }

    @Override
    public void end() throws IOException {
        try {
            mhEnd.invoke(delegate);
        } catch (IOException x) {
            throw x;
        } catch (Throwable e) {
            throw new IllegalArgumentException("Error calling TokenStream.end", e);
        }
    }

    @Override
    public void reset() throws IOException {
        try {
            mhReset.invoke(delegate);
        } catch (IOException x) {
            throw x;
        } catch (Throwable e) {
            throw new IllegalArgumentException("Error calling TokenStream.reset", e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            mhClose.invoke(delegate);
        } catch (IOException x) {
            throw x;
        } catch (Throwable e) {
            throw new IllegalArgumentException("Error calling TokenStream.close", e);
        }
    }

    @Override
    public int hashCode() {
        try {
            return (int) mhHashcode.invoke(delegate);
        } catch (Throwable e) {
            throw new IllegalArgumentException("Error calling TokenStream.hashcode", e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        try {
            return (boolean) mhEquals.invoke(delegate, obj);
        } catch (Throwable e) {
            throw new IllegalArgumentException("Error calling TokenStream.equals", e);
        }
    }

    @Override
    public String toString() {
        try {
            return (String) mhToString.invoke(delegate);
        } catch (Throwable e) {
            throw new IllegalArgumentException("Error calling TokenStream.toString", e);
        }
    }

    @Override
    public final boolean incrementToken() throws IOException {
        try {
            return (boolean) mhIncrementToken.invoke(delegate);
        } catch (IOException x) {
            throw x;
        } catch (Throwable e) {
            throw new IllegalArgumentException("Error calling TokenStream.hashcode", e);
        }
    }

    public String getCharTerm() {
        return termAtt.toString();
    }
}

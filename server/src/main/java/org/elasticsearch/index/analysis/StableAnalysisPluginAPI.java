/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.index.analysis;

import org.apache.lucene.analysis.TokenStream;

public abstract class StableAnalysisPluginAPI {
    abstract void ensureBackwardCompatibility(Object current, Class<?> oldVersion);
    abstract void ensureForwardCompatibility(Object current, Class<?> newVersion);

    void patchFromOtherLoader(Object delegate, ClassLoader loader, String canonicalClassName) {
        Class<?> clazz;
        try {
            clazz = loader.loadClass(canonicalClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("You must bring Lucene core and analysis with your plugin as a dependency.");
        }

        // If the class public interface ever breaks in the future we'll need to use
        // reflection or method handles to ensure we retain binary compatibility with
        // the plugins MIN lucene version.
        ensureBackwardCompatibility(delegate, clazz);
    }

    static boolean instanceOrSubclass(Class<?> clazz, String matchingClassName) {
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

    static void ensureClassCompatibility(Class<?> clazz, String matchingName) {
        if (instanceOrSubclass(clazz, matchingName) == false) {
            throw new IllegalArgumentException("You must provide a Lucene TokenStream.class instance");
        }
    }
}

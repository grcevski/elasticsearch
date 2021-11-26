/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugin.analysis.test;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.MatcherAssert;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static org.hamcrest.Matchers.instanceOf;

public class TestAnalysisTests extends ESTestCase {
    public void testDefaultsTestPluginAnalysis() throws IOException {
        final TestAnalysis analysis = createTestAnalysis(new Index("test", "_na_"), Settings.EMPTY, new AnalysisTestPlugin());
        TokenFilterFactory tokenizerFactory = analysis.tokenFilter.get("analysis_test_word_filter");
        MatcherAssert.assertThat(tokenizerFactory, instanceOf(ElasticWordTokenFilterFactory.class));
    }

    public class PluginClassLoader extends ClassLoader {
        public PluginClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith("org.elasticsearch.plugin.analysis")) {
                return getClass(name);
            }

            return super.loadClass(name);
        }

        private Class<?> getClass(String name) throws ClassNotFoundException {
            String file = name.replace('.', File.separatorChar) + ".class";
            byte[] byteArr = null;
            try {
                byteArr = loadClassData(file);
                Class<?> c = defineClass(name, byteArr, 0, byteArr.length);
                resolveClass(c);
                return c;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        private byte[] loadClassData(String name) throws IOException {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
            int size = stream.available();
            byte buff[] = new byte[size];
            DataInputStream in = new DataInputStream(stream);
            in.readFully(buff);
            in.close();
            return buff;
        }
    }

    private Class loadPluginClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader).asSubclass(Plugin.class);
        } catch (ClassNotFoundException e) {
            throw new ElasticsearchException("Could not find plugin class [" + className + "]", e);
        }

    }

    public void testDifferentClassloaders() throws Exception {
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            PluginClassLoader pluginLoader = new PluginClassLoader(this.getClass().getClassLoader());
            try {
                AnalysisPlugin plugin = (AnalysisPlugin)loadPluginClass(
                    AnalysisTestPlugin.class.getCanonicalName(),
                    pluginLoader).getDeclaredConstructor(null).newInstance();

                TokenFilterFactory factory = plugin
                    .getTokenFilters()
                    .get("analysis_test_word_filter")
                    .get(null, "analysis_test_word_filter");

                System.out.println(factory);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        });
    }
}

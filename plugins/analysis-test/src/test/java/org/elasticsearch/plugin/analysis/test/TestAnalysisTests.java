/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.plugin.analysis.test;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.ElisionFilter;
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
import java.io.StringReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;

import static org.apache.lucene.analysis.BaseTokenStreamTestCase.newAttributeFactory;
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

        private Class<?> getClass(String name) {
            String file = name.replace('.', File.separatorChar) + ".class";
            byte[] byteArr;
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
            if (stream == null) {
                return new byte[0];
            }
            int size = stream.available();
            byte[] buff = new byte[size];
            DataInputStream in = new DataInputStream(stream);
            in.readFully(buff);
            in.close();
            return buff;
        }
    }

    private Class<?> loadPluginClass(String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader).asSubclass(Plugin.class);
        } catch (ClassNotFoundException e) {
            throw new ElasticsearchException("Could not find plugin class [" + className + "]", e);
        }
    }

    private List<String> process(TokenStream filter) throws IOException {
        List<String> tas = new ArrayList<>();
        CharTermAttribute termAtt = filter.getAttribute(CharTermAttribute.class);
        filter.reset();
        while (filter.incrementToken()) {
            tas.add(termAtt.toString());
        }
        filter.end();
        filter.close();
        return tas;
    }

    public void testDifferentClassloaders() {
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

                String test = "Plop, juste pour voir l'embrouille avec Nikola et l'Elastic plug-ins. M'enfin.";
                Tokenizer tokenizer = new StandardTokenizer(newAttributeFactory());
                tokenizer.setReader(new StringReader(test));
                CharArraySet articles = new CharArraySet(asSet("l", "M"), false);
                TokenFilter filter = new ElisionFilter(tokenizer, articles);
                TokenStream pluginStream = factory.create(filter);

                List<String> pluginTas = process(pluginStream);

                assertEquals("Elastic", pluginTas.get(0));
            } catch (Exception e) {
                fail("Shouldn't reach here");
            }

            return null;
        });
    }
}

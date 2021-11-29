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
import org.apache.lucene.util.AttributeSource;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.SuppressForbidden;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESTestCase;
import org.hamcrest.MatcherAssert;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.lucene.analysis.BaseTokenStreamTestCase.newAttributeFactory;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
            String fileName = name.replaceAll("\\.", "/") + ".class";

            try {
                byte[] byteArr = loadClassData(fileName);
                Class<?> c = defineClass(name, byteArr, 0, byteArr.length);
                resolveClass(c);
                return c;
            } catch (IOException e) {
                throw new RuntimeException("Unable to load class data?", e);
            }
        }

        private byte[] loadClassData(String fileName) throws IOException {
            return getClass().getClassLoader().getResourceAsStream(fileName).readAllBytes();
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
                AnalysisPlugin plugin = (AnalysisPlugin) loadPluginClass(AnalysisTestPlugin.class.getCanonicalName(), pluginLoader)
                    .getConstructor((Class<?>[]) null)
                    .newInstance();

                TokenFilterFactory factory = plugin.getTokenFilters()
                    .get("analysis_test_word_filter")
                    .get(null, "analysis_test_word_filter");

                String test = "Plop, juste pour voir l'embrouille avec Nikola et l'Elastic plug-ins. M'enfin.";
                Tokenizer tokenizer = new StandardTokenizer(newAttributeFactory());
                tokenizer.setReader(new StringReader(test));
                CharArraySet articles = new CharArraySet(asSet("l", "M"), false);
                TokenFilter filter = new ElisionFilter(tokenizer, articles);
                TokenStream pluginStream = factory.create(filter);

                assertNotEquals(tokenizer.getClass().getClassLoader(), pluginStream.getClass().getClassLoader());

                List<String> pluginTas = process(pluginStream);

                assertEquals("Elastic", pluginTas.get(0));
            } catch (Exception e) {
                fail("Shouldn't reach here");
            }

            return null;
        });
    }

    @SuppressForbidden(reason = "We're forced to uses Class#getDeclaredMethods() here because this test checks public class contracts")
    public void testTokenStreamMethodsStability() {
        // AttributeSource doesn't have other superclasses than j.l.Object
        assertEquals(Object.class, AttributeSource.class.getSuperclass());
        // TokenStream directly extends AttributeSource
        assertEquals(AttributeSource.class, TokenStream.class.getSuperclass());

        final List<String> publicTSMethods = Arrays.stream(TokenStream.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::toGenericString)
            .collect(Collectors.toCollection(ArrayList::new));

        assertThat(
            publicTSMethods,
            containsInAnyOrder(
                "public abstract boolean org.apache.lucene.analysis.TokenStream.incrementToken() throws java.io.IOException",
                "public void org.apache.lucene.analysis.TokenStream.close() throws java.io.IOException",
                "public void org.apache.lucene.analysis.TokenStream.end() throws java.io.IOException",
                "public void org.apache.lucene.analysis.TokenStream.reset() throws java.io.IOException"
            )
        );

        final List<String> publicASMethods = Arrays.stream(AttributeSource.class.getDeclaredMethods())
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .map(Method::toGenericString)
            .collect(Collectors.toCollection(ArrayList::new));

        assertThat(
            publicASMethods,
            containsInAnyOrder(
                "public boolean org.apache.lucene.util.AttributeSource.equals(java.lang.Object)",
                "public final <T extends org.apache.lucene.util.Attribute> T "
                    + "org.apache.lucene.util.AttributeSource.addAttribute(java.lang.Class<T>)",
                "public final <T extends org.apache.lucene.util.Attribute> T "
                    + "org.apache.lucene.util.AttributeSource.getAttribute(java.lang.Class<T>)",
                "public final boolean org.apache.lucene.util.AttributeSource.hasAttribute(java.lang.Class<? "
                    + "extends org.apache.lucene.util.Attribute>)",
                "public final boolean org.apache.lucene.util.AttributeSource.hasAttributes()",
                "public final java.lang.String org.apache.lucene.util.AttributeSource.reflectAsString(boolean)",
                "public final java.util.Iterator<java.lang.Class<? extends org.apache.lucene.util.Attribute>> "
                    + "org.apache.lucene.util.AttributeSource.getAttributeClassesIterator()",
                "public final java.util.Iterator<org.apache.lucene.util.AttributeImpl> "
                    + "org.apache.lucene.util.AttributeSource.getAttributeImplsIterator()",
                "public final org.apache.lucene.util.AttributeFactory org.apache.lucene.util.AttributeSource.getAttributeFactory()",
                "public final org.apache.lucene.util.AttributeSource org.apache.lucene.util.AttributeSource.cloneAttributes()",
                "public final org.apache.lucene.util.AttributeSource$State org.apache.lucene.util.AttributeSource.captureState()",
                "public final void org.apache.lucene.util.AttributeSource.addAttributeImpl(org.apache.lucene.util.AttributeImpl)",
                "public final void org.apache.lucene.util.AttributeSource.clearAttributes()",
                "public final void org.apache.lucene.util.AttributeSource.copyTo(org.apache.lucene.util.AttributeSource)",
                "public final void org.apache.lucene.util.AttributeSource.endAttributes()",
                "public final void org.apache.lucene.util.AttributeSource.reflectWith(org.apache.lucene.util.AttributeReflector)",
                "public final void org.apache.lucene.util.AttributeSource.removeAllAttributes()",
                "public final void org.apache.lucene.util.AttributeSource.restoreState(org.apache.lucene.util.AttributeSource$State)",
                "public int org.apache.lucene.util.AttributeSource.hashCode()",
                "public java.lang.String org.apache.lucene.util.AttributeSource.toString()"
            )
        );
    }
}

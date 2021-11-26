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
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexSettings;

import java.util.HashSet;
import java.util.Set;

public abstract class PluginTokenFilterFactory extends AbstractTokenFilterFactory {

    private final String name;
    private final Set<Class<? extends Attribute>> pluginUsedAttributes = new HashSet<>();

    public PluginTokenFilterFactory(IndexSettings indexSettings, String name, Settings settings) {
        super(indexSettings, name, settings);
        this.name = name;
        Analysis.checkForDeprecatedVersion(name, settings);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public final TokenStream create(TokenStream tokenStream) {
        ESTokenStream pluginWrappedTokenStream = create(new ESTokenStream(tokenStream));
        return unwrapAndAddAttributes(pluginWrappedTokenStream);
    }

    private TokenStream unwrapAndAddAttributes(ESTokenStream tokenStream) {
        TokenStream result = tokenStream.getDelegate();
        for (Class<? extends Attribute> attributeClass : pluginUsedAttributes) {
            addAttributeClass(result, attributeClass);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private void addAttributeClass(TokenStream stream, Class<? extends Attribute> attributeClass) {
        ClassLoader ourClassLoader = ESTokenStream.class.getClassLoader();
        if (attributeClass.getClassLoader().equals(ourClassLoader) == false) {
            String className = attributeClass.getName();
            try {
                Class<?> attrClass = Class.forName(className, true, ourClassLoader);
                stream.addAttribute((Class<? extends Attribute>) attrClass);
            } catch (Exception x) {
                // some error handling?
            }
        }
    }

    void usingAttribute(Class<? extends Attribute> attributeClass) {
        pluginUsedAttributes.add(attributeClass);
    }

    public abstract ESTokenStream create(ESTokenStream input);

    public static ESTokenStream wrap(Object tokenStream) {
        return ESTokenStream.wrap(tokenStream);
    }
}

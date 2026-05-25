/*
 * Copyright (C) 2023-2024 The java-keyserver project team.
 *
 * SPDX-License-Identifier: EUPL-1.2 OR Apache-2.0
 */
package io.github.bmarwell.keyserver.test.utils.cdi;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.List;

public class SimpleInstance<T> implements Instance<T> {

    private final List<T> elements;

    private SimpleInstance(List<T> elements) {
        this.elements = List.copyOf(elements);
    }

    public static <T> SimpleInstance<T> of(T element) {
        return new SimpleInstance<T>(List.of(element));
    }

    public static <T> SimpleInstance<T> empty() {
        return new SimpleInstance<>(List.of());
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        return null;
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        return null;
    }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return null;
    }

    @Override
    public boolean isUnsatisfied() {
        return false;
    }

    @Override
    public boolean isAmbiguous() {
        return false;
    }

    @Override
    public void destroy(T instance) {}

    @Override
    public Handle<T> getHandle() {
        return null;
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
        return null;
    }

    @Override
    public T get() {
        return null;
    }

    @Override
    public Iterator<T> iterator() {
        return elements.iterator();
    }
}

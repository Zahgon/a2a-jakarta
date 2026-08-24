/*
 * Copyright The A2A Spring Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.a2a.spring.common;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Adapts a Spring {@link ObjectProvider} to the CDI {@link Instance} contract.
 *
 * <p>The A2A SDK is built as a CDI bean archive and several of its classes declare CDI field
 * injection points such as {@code @Inject @PublicAgentCard Instance<AgentCard>} and
 * {@code @Inject @Any Instance<TaskAuthorizationProvider>}. Spring's
 * {@code AutowiredAnnotationBeanPostProcessor} treats {@code jakarta.inject.Inject} as an autowire
 * annotation whenever {@code jakarta.inject-api} is on the classpath, so it adopts those injection
 * points as its own but cannot satisfy them: {@code Instance} is a CDI type Spring knows nothing
 * about.
 *
 * <p>Suppressing {@code @Inject} instead is not viable. SDK classes such as
 * {@code DefaultRequestHandler} rely on CDI to populate fields that have no builder or constructor
 * equivalent (its {@code @PostConstruct} dereferences an injected {@code A2AConfigProvider}), so the
 * injection must actually happen. Removing {@code jakarta.inject-api} from the classpath is not
 * viable either, because {@code Instance} extends {@code jakarta.inject.Provider} and the field type
 * would no longer resolve during Spring's autowiring-metadata scan.
 *
 * <p>The bridge therefore lets Spring perform the injection exactly as CDI did, and only supplies
 * the missing {@code Instance} container type. Lookup-by-qualifier ({@code select}) and the CDI
 * {@code Bean} metadata have no Spring equivalent and throw
 * {@link UnsupportedOperationException}; the SDK does not exercise them.
 */
public class SpringCdiInstance<T> implements Instance<T> {

    private final ObjectProvider<T> provider;

    public SpringCdiInstance(ObjectProvider<T> provider) {
        this.provider = provider;
    }

    @Override
    public T get() {
        return provider.getObject();
    }

    @Override
    public Iterator<T> iterator() {
        return provider.stream().iterator();
    }

    @Override
    public boolean isUnsatisfied() {
        return provider.stream().findAny().isEmpty();
    }

    @Override
    public boolean isAmbiguous() {
        return provider.stream().limit(2).count() > 1;
    }

    @Override
    public void destroy(T instance) {
        // Spring manages the lifecycle of its own singletons; CDI-style destruction does not apply.
    }

    @Override
    public Instance<T> select(Annotation... qualifiers) {
        throw new UnsupportedOperationException("CDI qualifier selection is not supported under Spring");
    }

    @Override
    public <U extends T> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException("CDI qualifier selection is not supported under Spring");
    }

    @Override
    public <U extends T> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        throw new UnsupportedOperationException("CDI qualifier selection is not supported under Spring");
    }

    @Override
    public Handle<T> getHandle() {
        return new SpringHandle<>(provider.getObject());
    }

    @Override
    public Iterable<? extends Handle<T>> handles() {
        return provider.stream().<Handle<T>>map(SpringHandle::new).toList();
    }

    private record SpringHandle<T>(T value) implements Handle<T> {

        @Override
        public T get() {
            return value;
        }

        @Override
        public Bean<T> getBean() {
            throw new UnsupportedOperationException("CDI Bean metadata is not available under Spring");
        }

        @Override
        public void destroy() {
            // See SpringCdiInstance#destroy.
        }

        @Override
        public void close() {
            // See SpringCdiInstance#destroy.
        }
    }
}

package com.guicedee.kotlin.lifecycle;

import com.google.inject.AbstractModule;
import com.guicedee.client.services.lifecycle.IGuiceModule;
import com.guicedee.kotlin.GuicedCoroutines;
import com.guicedee.modules.services.jsonrepresentation.IJsonRepresentation;
import tools.jackson.module.kotlin.KotlinModule;

/** Registers the coroutine owner and Kotlin support in the shared Jackson 3 mapper. */
public final class GuicedKotlinModule extends AbstractModule implements IGuiceModule<GuicedKotlinModule> {
    @Override
    protected void configure() {
        IJsonRepresentation.registerModule(new KotlinModule.Builder().build());
        bind(GuicedCoroutines.class).asEagerSingleton();
    }
}

package com.guicedee.kotlin.lifecycle;

import com.google.inject.Key;
import com.guicedee.client.IGuiceContext;
import com.guicedee.client.services.lifecycle.IGuicePreDestroy;
import com.guicedee.kotlin.GuicedCoroutines;

/** Drains coroutine cleanup before persistence pools, clients and the shared Vert.x runtime close. */
public final class KotlinShutdown implements IGuicePreDestroy<KotlinShutdown> {
    @Override
    public void onDestroy() {
        var context = IGuiceContext.contexts.get("default");
        if (context == null) return;
        context.existingInjector().ifPresent(injector -> {
            var binding = injector.getExistingBinding(Key.get(GuicedCoroutines.class));
            if (binding != null) binding.getProvider().get().close();
        });
    }

    @Override
    public Integer shutdownSortOrder() {
        return Integer.MAX_VALUE - 2000;
    }
}

package com.guicedee.kotlin.example;

import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.GuiceApplication;
import com.guicedee.client.utils.LogUtils;
import org.apache.logging.log4j.Level;

/** Starts the normal GuicedEE HTTP runtime; GET /kotlin/hello/world returns a Future-backed response. */
public final class Main {
    public static void main(String[] args) {
        GuiceApplication.run(() -> {
            LogUtils.addHighlightedConsoleLogger(Level.DEBUG);
            IGuiceContext.registerModule("com.guicedee.kotlin.example");
            var config = IGuiceContext.instance().getConfig();
            config.setClasspathScanning(true).setAnnotationScanning(true).setMethodInfo(true).setFieldInfo(true);
        });
    }
}

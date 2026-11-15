module com.guicedee.kotlin.test {
    requires com.guicedee.kotlin;
    requires com.guicedee.jsonrepresentation;
    requires tools.jackson.databind;
    requires org.junit.jupiter.api;
    uses com.guicedee.client.services.lifecycle.IGuiceModule;
    uses com.guicedee.client.services.lifecycle.IGuicePreDestroy;
    opens com.guicedee.kotlin.test to org.junit.platform.commons, com.google.guice, tools.jackson.databind, tools.jackson.module.kotlin, kotlin.reflect;
}

module com.guicedee.kotlin.mutiny.test {
    requires com.guicedee.kotlin.mutiny;
    requires org.junit.jupiter.api;
    opens com.guicedee.kotlin.mutiny.test to org.junit.platform.commons, com.google.guice;
}

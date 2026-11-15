module mutiny.kotlin {
    requires transitive io.smallrye.mutiny;
    requires transitive kotlin.stdlib;
    requires transitive kotlinx.coroutines.core;
    exports io.smallrye.mutiny.coroutines;
}

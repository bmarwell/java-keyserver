module io.github.bmarwell.keyserver.tsid {
    requires /* transitive, but automatic modules */ io.hypersistence.tsid;
    requires static jakarta.cdi;

    exports io.github.bmarwell.keyserver.tsid;
}

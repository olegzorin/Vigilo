package dev.olegz.vf.core.cache;

class CacheInvalidationEvent {
    public String server;
    public String cache;
    public Object key;

    public CacheInvalidationEvent() {
    }

    CacheInvalidationEvent(String cache, Object key) {
        this.server = CaffeineCacheInvalidator.INSTANCE_ID;
        this.cache = cache;
        this.key = key;
    }

    @Override
    public String toString() {
        return "server=" + server + ", cache=" + cache + ", key=" + key;
    }
}

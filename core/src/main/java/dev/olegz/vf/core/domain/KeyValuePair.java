package dev.olegz.vf.core.domain;

import java.util.HashMap;
import java.util.List;

public class KeyValuePair<K,V> {
    public K key;
    public V value;

    public KeyValuePair() {        
    }

    public KeyValuePair(K key, V value) {
        this.key = key;
        this.value = value;
    }
    
    @Override
    public String toString() {
        return key + ":" + value;
    }

    @SuppressWarnings("unchecked")
    public static <K,V> KeyValuePair<K,V>[] array(int size) {
        return (KeyValuePair<K,V>[]) new KeyValuePair[size];
    }

    public static <K,V> HashMap<K, V> toMap(List<KeyValuePair<K, V>> values) {
        HashMap<K, V> map = new HashMap<>(values.size());
        values.forEach(kv -> map.put(kv.key, kv.value));
        return map;
    }
}

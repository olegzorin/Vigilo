package dev.olegz.vf.core.cache;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.springframework.cache.interceptor.KeyGenerator;

public class MethodCacheKeyGenerator implements KeyGenerator {
    public static final String ID = "methodCacheKeyGenerator";

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder key = new StringBuilder(300).append(method.getName());
        for (Object p : params) {
            if (p == null) key.append(":null");
            else key.append(':').append(p.getClass().getName()).append('>').append(toKey(p));
        }
        return key.toString();
    }
    
    private static String toKey(Object p) {
        return p instanceof int[] a ? Arrays.toString(a) :
               p instanceof byte[] a ? Arrays.toString(a) :
               p instanceof long[] a ? Arrays.toString(a) :
               p instanceof short[] a ? Arrays.toString(a) :
               p instanceof char[] a ? Arrays.toString(a) :
               p instanceof Object[] a ? Arrays.toString(a) :
               p.toString();
    }
}

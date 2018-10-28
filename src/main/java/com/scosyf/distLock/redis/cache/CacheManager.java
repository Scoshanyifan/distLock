package com.scosyf.distLock.redis.cache;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;


public interface CacheManager {

    /**
     * @param key
     * @param value
     */
    <T> CacheResult<T> put(String key, Serializable value);

    /**
     * @param key
     * @param value
     * @param expire seconds
     */
    <T> CacheResult<T> put(String key, Serializable value, long expire);

    /**
     * 获取对象
     * 
     * @param key
     * @return
     */
    <T extends Serializable> CacheResult<T> getObject(String key);

    CacheResult<String> getString(String key);

    /**
     * @param key
     * @param expire 秒
     */
    void expire(String key, long expire);

    /**
     * @param key
     * @param expireDate 日期
     */
    void expireAt(String key, Date expireDate);

    void del(String key);

    /**
     * pv自增
     * 
     * @param namespace
     * @param key
     */
    long incPv(String namespace, String key);

    /**
     * 获取pv
     * 
     * @param namespace
     * @param key
     * @return
     */
    Long getPv(String namespace, String key);

    /**
     * 释放空间
     * 
     * @param namespace
     * @param key
     * @return
     */
    Long getAndDelPv(String namespace, String key);

    /**
     * 自增
     * 
     * @param key
     * @return 自增后的值
     */
    long incr(String key);

    void hput(String key, String hashKey, Long value);

    void hdel(String key, Object... hashKeys);

    <T> CacheResult<T> hget(String key, String hashKey);

    boolean setnx(String key, Long value);

    boolean setnx(String key, String value);
    
    String getSet(String key, String value);

    Set<String> keys(String keyPattern);

    long getExpire(String key);
    /**
     * 该Set中是否存在该元素
     * @param setName Set的名称
     * @param values  要判断的元素
     * @return
     */
    boolean isMemberOfSet(String setName, Serializable value);
    /**
     * 向Set中添加元素
     * @param setName Set的名称
     * @param value 要添加的元素
     * @return
     */
    long addToSet(String setName, Serializable... values);
    
    long delFromSet(String setName, Serializable value);
    
    long sizeOfSet(String setName);
}

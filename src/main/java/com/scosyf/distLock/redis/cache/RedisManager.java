package com.scosyf.distLock.redis.cache;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


@Component("redisManager")
public class RedisManager implements CacheManager{
    
    @Resource
    private RedisTemplate<String, Serializable>     redisTemplate;
    @Autowired
    private StringRedisTemplate                     stringRedisTemplate;

    @Override
    public <T> CacheResult<T> put(String key, Serializable value) {

        return put(key, value, -1L);
    }

    @Override
    public <T> CacheResult<T> put(String key, Serializable value, long expire) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        //数据不过期
        if (expire < 0) {
            if (value instanceof String) {
                stringRedisTemplate.opsForValue().set(key, (String) value);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
        } else {
            if (value instanceof String) {
                stringRedisTemplate.opsForValue().set(key, (String) value, expire, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(key, value, expire, TimeUnit.SECONDS);
            }
        }
        return CacheResult.of(true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Serializable> CacheResult<T> getObject(String key) {
        if (redisTemplate.opsForValue() != null) {
            T value = (T) redisTemplate.opsForValue().get(key);
            return CacheResult.of(true, value);
        } else {
            return CacheResult.of(false);
        }
    }

    @Override
    public CacheResult<String> getString(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        return CacheResult.of(true, value);
    }

    @Override
    public void expire(String key, long expire) {
        redisTemplate.expire(key, expire, TimeUnit.SECONDS);
    }

    @Override
    public void expireAt(String key, Date expireDate) {
        redisTemplate.expireAt(key, expireDate);
    }

    @Override
    public void del(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public long incPv(String namespace, String key) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public Long getPv(String namespace, String key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Long getAndDelPv(String namespace, String key) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public long incr(String key) {
        return redisTemplate.opsForValue().increment(key, 1);
    }

    @Override
    public void hput(String key, String hashKey, Long value) {
        if (StringUtils.isNotBlank(key)) {
            redisTemplate.opsForHash().put(key, hashKey, value);
        }
    }

    @Override
    public void hdel(String key, Object... hashKeys) {
        if (StringUtils.isNotBlank(key)) {
            redisTemplate.opsForHash().delete(key, hashKeys);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CacheResult<T> hget(String key, String hashKey) {
        T value = (T) redisTemplate.opsForHash().get(key, hashKey);
        return CacheResult.of(true, value);
    }

    @Override
    public boolean setnx(String key, Long value) {
        return redisTemplate.opsForValue().setIfAbsent(key, value);
    }

    @Override
    public boolean setnx(String key, String value) {
        return redisTemplate.opsForValue().setIfAbsent(key, value);
    }
    
    @Override
    public String getSet(String key, String value) {
        return (String) redisTemplate.opsForValue().getAndSet(key, value);
    }

    @Override
    public Set<String> keys(String keyPattern) {
        return redisTemplate.keys(keyPattern);
    }

    @Override
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key);
        if (expire == null) {
            return 0;
        }
        return expire.longValue();
    }

    @Override
    public boolean isMemberOfSet(String setName, Serializable value) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long addToSet(String setName, Serializable... values) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long delFromSet(String setName, Serializable value) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long sizeOfSet(String setName) {
        // TODO Auto-generated method stub
        return 0;
    }

}

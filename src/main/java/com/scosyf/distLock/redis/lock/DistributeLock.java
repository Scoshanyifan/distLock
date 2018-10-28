package com.scosyf.distLock.redis.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * 基于Jedis的原生分布式锁（单机正确版）
 * 
 * 分布式锁为了解决：
 *      1.安全独享，任意时刻只有一个线程持有
 *          > value需要唯一标识，系统时间+id（每台服务器时间须一致）
 *          > 补充问题：如何保证过期时间大于业务时间 TODO
 *      2.无死锁：即使持锁线程崩溃或网络问题，锁仍能正常释放被其他线程获取
 *          > 设置锁过期时间（重试时间）
 *          > 避免释放其他线程锁，需要在释放前检查value，且保证get和del是原子性的（i++）
 *      3.容错：若大部分redis节点活着，线程就可以获取和释放锁（多节点环境）
 *
 *      
 * 多节点用Redlock     
 * http://www.redis.cn/topics/distlock
 * https://juejin.im/post/5b737b9b518825613d3894f4
 */
@Component
public class DistributeLock {
    
    private static Logger logger = LoggerFactory.getLogger(DistributeLockBasic.class);

    /** 锁过期时间 */
    public static final long LOCK_EXPIRE_TIME = 300L;

    /** 重试时间 */
    public static final long LOCK_TIMEOUT = 100L;

    /** key前缀 */
    public static final String LOCK_KEY_PREFIX = "cache:lock:dist:";
    
    @Autowired
    private JedisPool jedisPool;
    
    /**
     * 直接用于业务
     * @param lockKey
     * @param handler
     * @return
     */
    public boolean lock(String lockKey, BizHandler handler) {
        boolean lock = false;
        String newLockTimeValue = Long.toString(System.currentTimeMillis() + LOCK_EXPIRE_TIME);
        try {
            lock = tryLock(lockKey, newLockTimeValue);
        } catch (Exception e) {
            logger.error("lock >>> try lock error >>> " + e);
            return false;
        }
        if (!lock) {
            return false;
        }
        try {
            return handler.doBiz();
        } finally {
            unLock(lockKey, newLockTimeValue);
        }
    }
    
    /**
     * 
     * @param lockKey
     * @param newValue
     * @return
     */
    public boolean tryLock(String lockKey, String newValue) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();

            Long lockBeginTime = System.currentTimeMillis();
            String key = LOCK_KEY_PREFIX + lockKey;
            for (;;) {
                if ((System.currentTimeMillis() - lockBeginTime) > LOCK_TIMEOUT) {
                    return false;
                }
                /**
                 * 保证设置过期时间和设置锁具有原子性（基础版中的合并）
                 * TODO 这个也避免了getset方法将原本没过期的锁的value刷新掉
                 * 
                 * set:如果key不存在(NX)则保存value，且设置过期毫秒(PX)时间
                 */
                String result = jedis.set(key, newValue, "NX", "PX", LOCK_EXPIRE_TIME);
                if ("OK".equals(result)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("try lock error >>> " + e);
            return false;
        } finally {
            if (jedis != null) {
                jedisPool.returnResource(jedis);
            }
        }
    }

    //lua脚本，将get和del合并为原子性操作
    public String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then " + 
                                  "return redis.call('del', KEYS[1]) " + 
                              "else " + 
                                  "return 0 " + 
                              "end";
    
    /**
     * 
     * @param lockKey
     * @param oldValue
     * @return
     */
    public boolean unLock(String lockKey, String oldValue) {
        Jedis jedis = null;
        try {
            jedis = jedisPool.getResource();
            String key = LOCK_KEY_PREFIX + lockKey;
            //
            Object result = jedis.eval(luaScript, Lists.newArrayList(key), Lists.newArrayList(oldValue));
            if ("1".equals(result)) {
                return true;
            }
        } catch (Exception e) {
            logger.error("unLock error >>> " + e);
        } finally {
            if (jedis != null) {
                jedisPool.returnResource(jedis);
            }
        }
        return false;
    }
    
}

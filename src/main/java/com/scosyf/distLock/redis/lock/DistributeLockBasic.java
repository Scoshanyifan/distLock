package com.scosyf.distLock.redis.lock;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scosyf.distLock.redis.cache.CacheManager;


/**
 * https://juejin.im/post/5b8737cdf265da43737ea13a
 * https://juejin.im/post/5b737b9b518825613d3894f4
 * 
 * 
 * 基于redis的分布式锁（基础版）
 * 
 * 流程如下： 
 *      [1] 通过setnx(key, value)设置锁，返回1表示设置成功，直接走业务；返回0表示加锁存在，走[2]验证锁是否失效
 *      [2] 用get(key)获取该锁下的旧值oldT（原时间戳），用现在的系统时间currT与之比较，若当前时间大，
 *          则表示锁已经过期，走[3]；否则表示锁还没过期，轮询重试 
 *      [3] 先设置好要重新加锁的时间戳：newT = currT +expT，然后用getSet(key, newValue)返回的oldValue
 *          和之前的oldT进行比较，若相同说明期间没有被其他线程枪锁，成功加锁，走业务；否则表示失败
 *      [4] 锁过期机制
 *      [5] 释放锁机制：保证释放的是自己的锁
 * 
 * 存在以下问题： 
 *      1.系统时间必须保证每台服务器都设置成一样 
 *      2.抢锁失败的线程会通过getSet(key, newValue)覆盖获取锁成功的过期时间
 *        导致：锁过期时间被覆盖，会造成锁不具有标识性，引起线程没有释放锁
 *      3.释放锁之前，刚好过期，此时其他线程上了锁，原线程如果调用unLock会释放掉其他线程的锁  
 *        所以释放前还需get(key)判断一次，但是get(key)和del(key)不是原子性
 *      
 * 
 */
public class DistributeLockBasic {

    private static Logger logger = LoggerFactory.getLogger(DistributeLockBasic.class);

    /** 锁过期时间 */
    public static final long LOCK_EXPIRE_TIME = 300L;

    /** 重试时间 */
    public static final long LOCK_TIMEOUT = 200L;
    /** 重试次数 */
    // public static final int LOOP_TIME = 50; //如果不使用轮询，则需要配置重试次数

    public static final String LOCK_KEY_PREFIX = "cache:lock:";
    
    /**
     * 获取锁
     * @param cacheManager
     * @param lockKey
     * @param bizHandler
     * @return
     */
    public static boolean lock(CacheManager cacheManager, String lockKey, BizHandler bizHandler) {
        boolean lock;
        // 加锁开始
        Long lockBeginTime = System.currentTimeMillis();
        // 设置加锁时间
        String newLockTime = Long.toString(lockBeginTime + LOCK_EXPIRE_TIME);
        
        try {
            // for (int i = 0; i < LOOP_TIME; i++) {
            for (;;) {
                // 检查是否超过重试时间
                if ((System.currentTimeMillis() - lockBeginTime) > LOCK_TIMEOUT) {
                    lock = false;
                    break;
                }
                if (innerLock(cacheManager, LOCK_KEY_PREFIX + lockKey, newLockTime)) {
                    lock = true ;
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("try lock error >>> " + e);
            lock = false;
        }
        //如果获取到锁则执行业务
        if (!lock) {
            return false;
        }
        try {
            return bizHandler.doBiz();
        } finally {
            //有可能释放锁的时候出现异常，即没拿到锁对应的时间
            try {
                unLock(cacheManager, lockKey, newLockTime);
            } catch (Exception e) {
                logger.error("unlock error >>> " + e);
            }
        }
    }

    /**
     * 内部获取锁机制
     * @param cacheManager
     * @param lockKey
     * @return
     */
    private static boolean innerLock(CacheManager cacheManager, String lockKey, String newLockTime) {
        
        // 设置锁的新时间
        // Long currentTime = System.currentTimeMillis();
        // String newLockTime = Long.toString(currentTime + LOCK_EXPIRE_TIME + 1);
        
        // 如果setnx返回true表示直接加锁成功
        if (cacheManager.setnx(lockKey, newLockTime)) {
            return true;
        }

        //锁已经存在的情况下，获取上次加锁时间
        String lastLockTime = (String) cacheManager.getObject(lockKey).getModule();
        //TODO 这里用getString()取到的value会带上\xAC\xED\x00\x05t\x00\x0D，原因是序列化问题
        //     虽然设置了String序列化器，但是貌似不行，用原始redisTemplate，配置中
        //     <property name="hashKeySerializer" ref="stringRedisSerializer" />

        // 如果锁不存在或者，当前系统时间 > 锁的旧时间，表示已过期，可以尝试上锁
        if (StringUtils.isNotBlank(lastLockTime) || System.currentTimeMillis() > Long.valueOf(lastLockTime)) { 
            // 检查加锁期间有没有被其他线程抢占，如果getset返回的和之前取到的一样，说明重新上锁成功
            // 但是如果没成功就代表着尝试上锁的线程将原先占锁的value刷新掉了
            // 导致执行线程释放锁的时候发现不是自己的锁，不释放，只能等系统过期
            String lastLockTimeCheck = cacheManager.getSet(lockKey, newLockTime);
            System.out.println("lastLockTime: " + lastLockTime + ", lastLockTimeCheck: " + lastLockTimeCheck);
            //先验证空，因为有可能已经系统过期
            if (StringUtils.isNotBlank(lastLockTimeCheck) || lastLockTimeCheck.equals(lastLockTime)) {
                System.out.println("lastLockTime: " + lastLockTime + ", lastLockTimeCheck: " + lastLockTimeCheck);
                // 主动设置锁过期时间
                cacheManager.expire(lockKey, LOCK_EXPIRE_TIME);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 释放锁
     * @param cacheManager
     * @param lockKey
     * @param value
     */
    private static void unLock(CacheManager cacheManager, String lockKey, String value) {
        String tempValue = cacheManager.getString(lockKey).getModule();
        //防止释放了不属于当前客户端的锁，因为有可能业务时间超过过期时间
        if (!StringUtils.isNotBlank(tempValue) && value.equals(tempValue)) {
            cacheManager.del(lockKey);  
        } 
    }

}

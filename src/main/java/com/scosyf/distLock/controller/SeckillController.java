package com.scosyf.distLock.controller;

import java.util.concurrent.atomic.LongAdder;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scosyf.distLock.redis.cache.CacheManager;
import com.scosyf.distLock.redis.lock.BizHandler;
import com.scosyf.distLock.redis.lock.DistributeLock;
import com.scosyf.distLock.redis.lock.DistributeLockBasic;


@RestController
@RequestMapping("/seckill")
public class SeckillController {

    private static Logger Logger            = LoggerFactory.getLogger(SeckillController.class);
    
    private static final long STOCK_NUMBER  = 10000L;
    private static LongAdder stock          = new LongAdder();   
    private static LongAdder stock2         = new LongAdder(); 
    private static long stock3              = STOCK_NUMBER;
    private static long stock4              = STOCK_NUMBER;
    
    static {
        stock.add(STOCK_NUMBER);
        stock2.add(STOCK_NUMBER);
    }
    
    @Autowired
    private DistributeLock distributeLock;
    @Autowired
    private CacheManager cacheManager;
    
    @GetMapping(value = "/v1", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String secKillByRedis() {
        long lockTime = System.currentTimeMillis() + DistributeLock.LOCK_EXPIRE_TIME;
        String key = "seckill";
        String value = Thread.currentThread().getName() + Long.toString(lockTime);
        if (!distributeLock.tryLock(key, value)) {
            return "抢的人太多了，慢慢来";
        }
        if (stock.longValue() == 0L) {
            return "抢完了";
        }
        doBiz(new BizHandler() {
            @Override
            public boolean doBiz() {
                stock.decrement();
                return true;
            }
        });
        distributeLock.unLock(key, value);
        
        long left = stock.longValue();
        return "已经抢了" + (STOCK_NUMBER - left) + ", 还剩下" + left;
    }
    
    @GetMapping(value = "/v2", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String seckillByAtomic() {
        if (stock2.longValue() <= 0) {
            return "已经抢完了";
        }
        doBiz(new BizHandler() {
            @Override
            public boolean doBiz() {
                stock2.decrement();
                return true;
            }
        });
        
        long left = stock2.longValue();
        return "已经抢了" + (STOCK_NUMBER - left) + ", 还剩下" + left;
    }
    
    @GetMapping(value = "/v3", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String seckillDefault() {
        if (stock3 <= 0) {
            return "已经抢完了";
        }
        doBiz(new BizHandler() {
            @Override
            public boolean doBiz() {
                stock3--;
                return true;
            }
        });
        
        long left = stock3; 
        return "已经抢了" + (STOCK_NUMBER - left) + ", 还剩下" + left;
    }
    
    @GetMapping(value = "/v4", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String seckillByRedisBasic() {
        boolean lock = DistributeLockBasic.lock(cacheManager, "seckill-basic", new BizHandler() {
            @Override
            public boolean doBiz() {
                stock4--;
                return true;
            }
        });
        if (!lock) {
            return "抢的太快了大家";
        }
        
        long left = stock4; 
        return "已经抢了" + (STOCK_NUMBER - left) + ", 还剩下" + left;
    }
    
    @GetMapping(value = "/stock", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String getStockDetail(HttpServletRequest request) {
        String v = request.getParameter("v");
        long left = -1L;
        if (v.equals("1")) {
            left = stock.longValue();
        } else if (v.equals("2")) {
            left = stock2.longValue();
        } else if (v.equals("3")) {
            left = stock3;
        } else if (v.equals("4")) {
            left = stock4;
        }
        return "仓库情况：剩余 " + left + ", 已经抢了 " + (STOCK_NUMBER - left);
    }
    
    private void doBiz(BizHandler handler) {
        try {
            Thread.sleep(200);
            handler.doBiz();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

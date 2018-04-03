package com.earnfish;

import java.io.Serializable;
import java.util.concurrent.locks.ReentrantLock;

public class MyConcurrentMap<K,V> {
	 // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long SBASE;
    private static final int SSHIFT;
    private static final long TBASE;
    private static final int TSHIFT;
    private static final long HASHSEED_OFFSET;
    private static final long SEGSHIFT_OFFSET;
    private static final long SEGMASK_OFFSET;
    private static final long SEGMENTS_OFFSET;

    static {
        int ss, ts;
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class tc = HashEntry[].class;
            Class sc = Segment[].class;
            TBASE = UNSAFE.arrayBaseOffset(tc);
            SBASE = UNSAFE.arrayBaseOffset(sc);
            ts = UNSAFE.arrayIndexScale(tc);
            ss = UNSAFE.arrayIndexScale(sc);
            HASHSEED_OFFSET = UNSAFE.objectFieldOffset(
            		MyConcurrentMap.class.getDeclaredField("hashSeed"));
            SEGSHIFT_OFFSET = UNSAFE.objectFieldOffset(
            		MyConcurrentMap.class.getDeclaredField("segmentShift"));
            SEGMASK_OFFSET = UNSAFE.objectFieldOffset(
            		MyConcurrentMap.class.getDeclaredField("segmentMask"));
            SEGMENTS_OFFSET = UNSAFE.objectFieldOffset(
            		MyConcurrentMap.class.getDeclaredField("segments"));
        } catch (Exception e) {
            throw new Error(e);
        }
        if ((ss & (ss-1)) != 0 || (ts & (ts-1)) != 0)
            throw new Error("data type scale not a power of two");
        SSHIFT = 31 - Integer.numberOfLeadingZeros(ss);
        TSHIFT = 31 - Integer.numberOfLeadingZeros(ts);
    }

	private int segmentShift;
	private int segmentMask;
    final Segment<K,V>[] segments;

	
	static final class HashEntry<K,V>{
		final int hash;
		final K key;
		volatile V value;
		

		volatile HashEntry<K,V> next;
		
		HashEntry(int hash, K key, V value, HashEntry<K,V> next){
			this.hash = hash;
			this.key = key;
			this.value = value;
			this.next = next;
		}
		
		public HashEntry<K, V> getNext() {
			return next;
		}

		public void setNext(HashEntry<K, V> next) {
			this.next = next;
		}
	}
	
	
	static final class Segment<K,V> extends ReentrantLock implements Serializable{
		
		private static final long serialVersionUID = 2249069246763182397L;
		
		static final int MAX_SCAN_RETRIES = Runtime.getRuntime().availableProcessors() > 1 ? 64 : 1;
		
		transient volatile HashEntry<K,V>[] table;
		
		/** 该segment的总元素个数*/
		transient int count;
		
		/** 该segment可以并发的总数 */
		transient int modCount;
		
		/** 会出发rehash操作*/
		transient int threshold;
		
		/** hash table的load factor */
		final float loadFactor;
		
		
		Segment(float lf, int threshold, HashEntry<K,V>[] tab){
			this.loadFactor = lf;
			this.threshold = threshold;
			this.table = tab;
		}
		
		
		
		
		final V put(K key, int hash, V value, boolean onlyIfAbsent) {
			/**尝试获取该segment的锁*/
			HashEntry<K,V> node = tryLock() ? null: scanAndLockForPut(key, hash, value);
			
			V oldValue;
			try {
				HashEntry<K,V> [] tab = table;
				
				int index = (tab.length -1 ) & hash;
				
				HashEntry<K,V> first = entryAt(tab, index);
				
				for(HashEntry<K,V> e = first;;) {
					/**当前位置已存在元素*/
					if(e != null) {
						K k;
						if((k=e.key) == key ||(e.hash == hash && key.equals(k))) {
							oldValue = e.value;
							if(!onlyIfAbsent) {
								e.value = value;
								++ modCount;
							}
							break;
						}
						e = e.next;
					}
					else {
						/**e == null，查看是否获得了锁，或者已经生成了节点*/
						if( node != null) {
							node.setNext(first);
						}else {
							node = new HashEntry<K,V>(hash,key,value,first);
						}
						
						int c = count + 1;
						if( c > threshold && tab.length < MAXIMUM_CAPACITY) {
							rehash(node);
						}else {
							setEntryAt(tab,index,node);
						}
						++modCount;
		                count = c;
		                oldValue = null;
		                break;
					}
				}
			}finally {
				unlock();
			}
		}
		
		/**
	     * Sets the ith element of given table, with volatile write
	     * semantics. (See above about use of putOrderedObject.)
	     */
	    static final <K,V> void setEntryAt(HashEntry<K,V>[] tab, int i,
	                                       HashEntry<K,V> e) {
	        UNSAFE.putOrderedObject(tab, ((long)i << TSHIFT) + TBASE, e);
	    }
		
		
		/**
	     * Gets the ith element of given table (if nonnull) with volatile
	     * read semantics. Note: This is manually integrated into a few
	     * performance-sensitive methods to reduce call overhead.
	     */
	    @SuppressWarnings("unchecked")
	    static final <K,V> HashEntry<K,V> entryAt(HashEntry<K,V>[] tab, int i) {
	        return (tab == null) ? null :
	            (HashEntry<K,V>) UNSAFE.getObjectVolatile
	            (tab, ((long)i << TSHIFT) + TBASE);
	    }
		
		
		@SuppressWarnings("unchecked")
	    static final <K,V> HashEntry<K,V> entryForHash(Segment<K,V> seg, int h) {
	        HashEntry<K,V>[] tab;
	        return (seg == null || (tab = seg.table) == null) ? null :
	            (HashEntry<K,V>) UNSAFE.getObjectVolatile
	            (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
	    }
		
		/**循环获取segment锁，并顺便对数组进行便利，将数据初始化到cpu中去，同时能增加自旋的等待时间，同时创建Node不用在锁中创建，降低锁的持续时间*/
		private HashEntry<K,V> scanAndLockForPut(K key, int hash, V value){
			HashEntry<K,V> first = entryForHash(this,hash);
			HashEntry<K,V> e = first;
			HashEntry<K,V> node = null;
			int retries = -1;
			
			while(!tryLock())
			{
				HashEntry<K,V> f ;
				if(retries < 0) {
					/** 链表中的该hash位置并没有这个元素，初始化一个新的出来*/
					if( e == null) {
						if( node== null) {
							/**并未获取到锁，而且该位置是空的*/
							node = new HashEntry<K,V>(hash, key, value, null);
						}
						retries = 0;
					}
					/**存在与该hash相同，且元素也相同的key*/
					else if( key.equals(e.key)) {
						retries = 0;
					/*e!=null链表中有这个hash，向后找是否有这个元素 */
					}else {
						e = e.next;
					}
				}
				
				else if( ++retries  > MAX_SCAN_RETRIES ) {
					lock();
					break;
				}
				
				else if((retries & 1) == 0 && (f = entryForHash(this,hash))!=first) {
					e = first = f;
					retries = -1;
				}
				
			}	
			return node;
		}
	}
	
	
	
	
	public V get(Object key) {
	    Segment<K,V> s; // manually integrate access methods to reduce overhead
	    HashEntry<K,V>[] tab;
	    // 1. hash 值
	    int h = hash(key);
	    long u = (((h >>> segmentShift) & segmentMask) << SSHIFT) + SBASE;
	    // 2. 根据 hash 找到对应的 segment
	    if ((s = (Segment<K,V>)UNSAFE.getObjectVolatile(segments, u)) != null &&
	        (tab = s.table) != null) {
	        // 3. 找到segment 内部数组相应位置的链表，遍历
	        for (HashEntry<K,V> e = (HashEntry<K,V>) UNSAFE.getObjectVolatile
	                 (tab, ((long)(((tab.length - 1) & h)) << TSHIFT) + TBASE);
	             e != null; e = e.next) {
	            K k;
	            if ((k = e.key) == key || (e.hash == h && key.equals(k)))
	                return e.value;
	        }
	    }
	    return null;
	}
	
	public V put(K key, V value) {
		Segment<K,V> s;
		if(value == null) {
			throw new NullPointerException();
		}
		/**计算key的hash值*/
		int hash = hash(key);
		
		/** 无符号右移28位，得到低位的4位，而后与15进行&运算*/
		int j = (hash >>> segmentShift) & segmentMask;
		
		/**检测当前的segment是否为null，如果是null则新增*/
		if ((s = (Segment<K,V>)UNSAFE.getObject(segments, (j << SSHIFT) + SBASE)) == null) {
			 //s = ensureSegment(j); 新增卡槽
		}
		
		/** 如果不是null，插入新值到segment*/
		return s.put(key,hash,value,false);
		
	}
	
	
	
	
	public MyConcurrentMap(int initialCapacity, float loadFactor, int concurrentLevel) {
		if(initialCapacity < 0 || loadFactor < 0 || concurrentLevel < 0) {
			throw new IllegalArgumentException("输入参数错误");
		}
		
		int sshift = 0;
		int ssize = 1;
		while(ssize < concurrentLevel) {
			++ sshift;
			ssize <<= 1;
		}
		
		this.segmentShift = 32 - sshift;
		this.segmentMask = ssize - 1;
		/**initialCapacity为map的初始总长度，ssize为分段数，c是每段的长度*/
		int c = initialCapacity / ssize;
		
		if(c * ssize < initialCapacity) {
			++c;
		}
		
		int cap =1;
		while(cap < c) {
			cap <<=1;
		}
		/** 创建segment以及segment[0]*/
		Segment<K,V> s0 = new Segment<K,V>(loadFactor,(int)(cap*loadFactor), (HashEntry<K,V>[])new HashEntry[cap]);
		Segment<K,V>[] ss =new Segment[ssize];
		
		/**
		 * not strictly necessary
		 * */
		UNSAFE.putOrderedObject(ss, SBASE, s0);
		
		/** 由于segments 是final类型，因此这里使用了一个临时变量ss做辅助*/
		this.segments = ss;
	}
	
	
	/**简化版的hash操作*/
	private int hash(K k) {
		int h;
		//将对象的hash值右移16位后与起原hash值做异或运算
		return (k==null)? 0: (h=k.hashCode()) ^ (h>>>16);
	}
	
}

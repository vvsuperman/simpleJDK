package com.earnfish;

import java.util.ArrayList;
import java.util.List;

public class MyHashMap<K, V> implements MyMap<K,V> {
	
	/** 默认大小 2^4 = 16 */
	private static final int DEFAULT_INITIAL_CAPACITY = 1 << 4;
	/** 阀值比例 */
	private static final float DEFAULT_LOAD_FACTOR = 0.75f;
	
	private int defaultInitSize;
	
	private float defaultLoadFactor;
	
	/**Map中entry的数量*/
	private int entryUseSize;
	
	private Entry<K,V>[] table = null;
	
	public MyHashMap() {
		this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
	}
	public MyHashMap(int defaultInitialCapacity, float defaultLoadFactor) {
		if(defaultInitialCapacity < 0) {
			throw new IllegalArgumentException("Illegal intial capacity:" + defaultInitialCapacity);
		}
		
		if(defaultLoadFactor <=0 || Float.isNaN(defaultLoadFactor)) {
			throw new IllegalArgumentException("Illegal load factor:" + defaultLoadFactor);
		}
		this.defaultInitSize = defaultInitialCapacity;
	    this.defaultLoadFactor = defaultLoadFactor;	
	    
	    table = new Entry[this.defaultInitSize];
	}	
	
	class Entry<K,V> implements MyMap.Entry<K, V>{
		
		private K key;
		private V value;
		private Entry<K,V> next;
		
		public Entry() {
		}
		
		public Entry(K key, V value, Entry<K,V> next) {
			this.key = key;
			this.value = value;
			this.next = next;
		}

		@Override
		public K getKey() {
			return key;
		}

		@Override
		public V getValue() {
			return value;
		}
	}
	

	@Override
	public V get(K k) {
		int index = hash(k) & (defaultInitSize -1);
		if(table[index] == null) {
			return null;
		}else {
			Entry<K,V> entry = table[index];
			do {
				if(k == entry.getKey() || k.equals(entry.getKey())) {
					return entry.value;
				}
				entry = entry.next;
			}while (entry != null);
		}
		return null;
	}
	
	/** 计算hash散列的值,“扰动函数”,对象的hashCode是怎么来的*/
	private int hash(K k) {
		int h;
		return (k == null) ? 0: (h = k.hashCode()) ^ (h >>> 16);
	}
	
	private void resize(int i) {
		Entry[] newTable = new Entry[i];
		defaultInitSize = i;
		entryUseSize = 0;
		rehash(newTable);
	}
	
	/** 为何要先加到list 再put到hashmap*/
	private void rehash(Entry<K,V>[] newTable) {
		List<Entry<K,V>> entryList = new ArrayList<Entry<K,V>>();
		for(Entry<K,V> entry:table) {
			if(entry != null) {
				do {
					entryList.add(entry);
					entry = entry.next;
				}while(entry != null);
			}
		}
		
		/**覆盖旧的引用*/
		if(newTable.length > 0) {
			table = newTable;
		}
		
		/**重新put entry到hashmap*/
		for(Entry<K,V> entry: entryList) {
			put(entry.getKey(), entry.getValue());
		}
		
	}
	
	@Override
	public V put(K k, V v) {
		V oldValue = null;
		/**判断是否需要扩容 */
		if(entryUseSize >= defaultInitSize * defaultLoadFactor) {
			resize(defaultInitSize * 2);
		}
		/** 计算hash值*/
		int index = hash(k) & (defaultInitSize - 1);
		/** 如果当前位置上是否有值*/
		if(table[index] == null) {
			table[index] = new Entry<K,V>(k,v,null);
			++ entryUseSize;
		}else {
			Entry<K,V> entry = table[index];
			Entry<K,V> e = entry;
			while(e != null) {
				/** == 以及equal方法 */
				if(k == e.getKey() || k.equals(e.getKey())) {
					oldValue = e.value;
					e.value = v;
					return oldValue;
				}
				e = e.next;
			}
			table[index] = new Entry<K,V>(k,v,entry);
			++ entryUseSize;	
		}
		return oldValue;
	}
	
	public static void main(String[] args) {
		MyHashMap<String, String> myMap = new MyHashMap<>();
		for(int i = 0;i<40;i++) {
			myMap.put("key"+i, "value"+i);
		}
		
		for(int i =0;i<40;i++) {
			System.out.println(myMap.get("key"+i));
		}
	}
}

package com.earnfish;

import java.nio.charset.Charset;
import java.util.Arrays;

public final class MyString implements java.io.Serializable,Comparable<String> {
	
	private final char value[];
	
	
    private int hash; // Default to 0

	
	public MyString(MyString original) {
		this.value = original.value;
		this.hash = original.hash;
	}
	
	 public MyString(char value[], int offset, int count) {
	        if (offset < 0) {
	            throw new StringIndexOutOfBoundsException(offset);
	        }
	        if (count <= 0) {
	            if (count < 0) {
	                throw new StringIndexOutOfBoundsException(count);
	            }
	        }
	        // Note: offset or count might be near -1>>>1.
	        if (offset > value.length - count) {
	            throw new StringIndexOutOfBoundsException(offset + count);
	        }
	        this.value = Arrays.copyOfRange(value, offset, offset+count);
	    }
	 
	 
	 /*
	    * Package private constructor which shares value array for speed.
	    * this constructor is always expected to be called with share==true.
	    * a separate constructor is needed because we already have a public
	    * String(char[]) constructor that makes a copy of the given char[].
	    */
	 MyString(char[] value, boolean share) {
	        // assert share : "unshared not supported";
	        this.value = value;
	 }
	
	public int length() {
		return value.length;
	}
	
	public boolean equals(Object anObject) {
		if(this == anObject) {
			return true;
		}
		if(anObject instanceof MyString) {
			MyString anotherString = (MyString)anObject;
			int n = value.length;
			if( n == anotherString.length()) {
				char v1[] = value;
				char v2[] = anotherString.value;
				int i = 0;
				/** i-- 要快*/
				while(n-- != 0) {
					if(v1[i] != v2[i]) {
						 return false;
					}
					i++;
				}
				return true;
			}
		}
		return false;
	}
	
	public int compareTo(MyString anotherString) {
		int len1 = value.length;
		int len2 = anotherString.value.length;
		int lim = Math.min(len1, len2);
		char v1[] = value;
		char v2[] = anotherString.value;
		
		int k = 0;
		while(k < lim) {
			char c1 = v1[k];
			char c2 = v2[k];
			if(c1 != c2) {
				return c1-c2;
			}
			k++;
		}
		return len1 - len2;
	}
	
	public int indexOf(int ch, int fromIndex) {
		final int max = value.length;
		if( fromIndex < 0) {
			fromIndex = 0;
		}else if( fromIndex >= max) {
			return -1;
		}
		
		if( ch < Character.MIN_SUPPLEMENTARY_CODE_POINT) {
			// handle most cases here (ch is a BMP code point or a
            // negative value (invalid code point))
			final char[] value = this.value;
			for(int i = fromIndex; i < max; i++) {
				if(value[i] == ch) {
					return i;
				}
			}
			return -1;
		}else {
			return indexOfSupplementary(ch, fromIndex);
		}
	}
	
	private int indexOfSupplementary(int ch, int fromIndex) {
		if(!Character.isValidCodePoint(ch)) 
			return -1;
		final char[] value = this.value;
		final char hi = Character.highSurrogate(ch);
		final char ho = Character.lowSurrogate(ch);
		final int max = value.length -1;
		for( int i = fromIndex; i < max; i++) {
			if(value[i] == hi && value[i+1] == ho) {
				return i;
			}
		}
		return -1;
	}
	
	
	public int indexOf(MyString str) {
		return indexOf(str, 0);
	}
	
	public int indexOf(MyString str, int fromIndex) {
        return indexOf(value, 0, value.length,
                str.value, 0, str.value.length, fromIndex);
    } 
	
	static int indexOf(char[] source, int sourceOffset, int sourceCount,
	            char[] target, int targetOffset, int targetCount,
	            int fromIndex) {
		
		 if(fromIndex > sourceCount) {
			 return (targetCount == 0 ? sourceCount : -1 );
		 }
		 if(fromIndex < 0) {
			 fromIndex = 0;
		 }
		 
		 char first = target[targetOffset];
		 int max = sourceOffset + (sourceCount - targetCount);
		 
		 for( int i = sourceOffset + fromIndex; i <= max; i++) {
			 /* Look for first character. */
			 if( source[i] != first) {
				 while(++i <= max && source[i] != first);
			 }
			 
			 /** then the rest */
			 if( i <= max) {
				 int j = i+1;
				 int end = j + targetCount -1;
				 for( int k = targetOffset + 1; j < end && source[j] == target[k]; j++,k++);
				 if(j == end)
					 return i - sourceOffset;
			 }
		 }
		return -1;
	}
	
	public MyString subString( int beginIndex, int endIndex) {
		if( beginIndex < 0)
			throw new StringIndexOutOfBoundsException( beginIndex);
		if( endIndex > value.length)
			throw new StringIndexOutOfBoundsException( endIndex);
		return ((beginIndex == 0) && (endIndex == 0))? this: new MyString(value,beginIndex,endIndex);
	}
	
	
	public static String reverse(String orig) {  
		  char[] s = orig.toCharArray();  
		  int n = s.length - 1;  
		  int halfLength = n / 2;  
		  for (int i = 0; i <= halfLength; i++) {  
		   char temp = s[i];  
		   s[i] = s[n - i];  
		   s[n - i] = temp;  
		  }  
		  return new String(s);  
	} 
	
	 public MyString concat(MyString str) {
	        int otherLen = str.length();
	        if (otherLen == 0) {
	            return this;
	        }
	        int len = value.length;
	        char buf[] = Arrays.copyOf(value, len + otherLen);
	        str.getChars(buf, len);
	        /** 不用copy方法来提高效率 */
	        return new MyString(buf,true);
	  }
	 
	 /**
	     * Copy characters from this string into dst starting at dstBegin.
	     * This method doesn't perform any range checking.
	     */
	  void getChars(char dst[], int dstBegin) {
	        System.arraycopy(value, 0, dst, dstBegin, value.length);
	  }

	@Override
	public int compareTo(String o) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	
	public static String byteToString(byte src) {
		StringBuilder result = new StringBuilder();
		for( int i=0;i<8;i++) {
			result.append(src%2 == 0 ? '0':'1');
            src = (byte)(src >>> 1);  
		}
		return result.reverse().toString();
	}
	
	public static void testChar(String str) throws Exception{
		System.out.println(Charset.defaultCharset());
		byte[] utf16 = str.getBytes("UTF-16");
		System.out.println(Arrays.toString(utf16));
		for( int i = 0; i < str.length(); i++) {
			System.out.println(str.charAt(i));
			byte high = (byte)(str.charAt(i) >>> 8);
			byte low = (byte)str.charAt(i);
            System.out.println(byteToString(high) + byteToString(low)); 
            System.out.println(byteToString(utf16[2+2*i]) + byteToString(utf16[2+2*i+1]));  

		}
	}
	
	public static void main(String[] args) throws Exception{
		//MyString.testChar("I am 中国人");
		String theStr ="中国€";
		for(int i = 0;i<theStr.length(); i++) {
			System.out.println(theStr.charAt(i));
		}
	}
	

	

	
}

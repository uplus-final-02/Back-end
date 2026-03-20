package common.util;

public class ChosungUtil {
	private static final char[] CHOSUNG = {
	        'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 
	        'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
	    };
	    private static final int HANGUL_BASE = 0xAC00;
	    private static final int HANGUL_END = 0xD7A3;

	    public static String extract(String text) {
	        if (text == null) return "";
	        StringBuilder sb = new StringBuilder();
	        for (char ch : text.toCharArray()) {
	            if (ch >= HANGUL_BASE && ch <= HANGUL_END) {
	                int chosungIndex = (ch - HANGUL_BASE) / (21 * 28);
	                sb.append(CHOSUNG[chosungIndex]);
	            } else {
	                sb.append(ch); 
	            }
	        }
	        return sb.toString();
	    }
}

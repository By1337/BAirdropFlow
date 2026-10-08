package dev.by1337.baf.util;

public class WildcardMatcher {

    public static boolean match(String pattern, String text) {
        int pLen = pattern.length();
        int tLen = text.length();
        int pIdx = 0;
        int tIdx = 0;
        int starIdx = -1;
        int matchIdx = 0;

        while (tIdx < tLen) {
            if (pIdx < pLen && (pattern.charAt(pIdx) == '?' ||
                    pattern.charAt(pIdx) == text.charAt(tIdx))) {
                pIdx++;
                tIdx++;
            } else if (pIdx < pLen && pattern.charAt(pIdx) == '*') {
                starIdx = pIdx;
                matchIdx = tIdx;
                pIdx++;
            } else if (starIdx != -1) {
                pIdx = starIdx + 1;
                matchIdx++;
                tIdx = matchIdx;
            } else {
                return false;
            }
        }
        while (pIdx < pLen && pattern.charAt(pIdx) == '*') {
            pIdx++;
        }
        return pIdx == pLen;
    }
}
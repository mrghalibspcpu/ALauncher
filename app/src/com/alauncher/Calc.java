package com.alauncher;

import java.text.DecimalFormat;

/** Inline calculator for the drawer search bar. */
public class Calc {

    public static String eval(String expr) {
        if (expr == null) return null;
        String s = expr.trim().replace("×", "*").replace("÷", "/").replace(",", "");
        if (s.isEmpty()) return null;
        boolean hasDigit = false, hasOp = false;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) hasDigit = true;
            else if ("+-*/^%".indexOf(c) >= 0) hasOp = true;
            else if (!"(). ".contains(String.valueOf(c))) return null;
        }
        if (!hasDigit || !hasOp) return null;
        try {
            double v = new P(s).parse();
            if (Double.isNaN(v) || Double.isInfinite(v)) return null;
            DecimalFormat df = new DecimalFormat("#.##########");
            return df.format(v);
        } catch (Throwable t) {
            return null;
        }
    }

    private static class P {
        private final String s;
        private int i;
        P(String s) { this.s = s; }

        double parse() {
            double v = expr();
            if (i < s.length()) throw new RuntimeException("bad");
            return v;
        }

        private void ws() { while (i < s.length() && s.charAt(i) == ' ') i++; }

        private boolean eat(char c) {
            ws();
            if (i < s.length() && s.charAt(i) == c) { i++; return true; }
            return false;
        }

        double expr() {
            double v = term();
            while (true) {
                if (eat('+')) v += term();
                else if (eat('-')) v -= term();
                else return v;
            }
        }

        double term() {
            double v = power();
            while (true) {
                if (eat('*')) v *= power();
                else if (eat('/')) v /= power();
                else if (eat('%')) v %= power();
                else return v;
            }
        }

        double power() {
            double v = unary();
            if (eat('^')) v = Math.pow(v, power());
            return v;
        }

        double unary() {
            if (eat('-')) return -unary();
            if (eat('+')) return unary();
            return atom();
        }

        double atom() {
            ws();
            if (eat('(')) {
                double v = expr();
                eat(')');
                return v;
            }
            int st = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            if (st == i) throw new RuntimeException("bad");
            return Double.parseDouble(s.substring(st, i));
        }
    }
}

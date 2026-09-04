package com.unifor.br.peer.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor e escritor de JSON minimo, suficiente para o protocolo do chat.
 *
 */
final class Json {

    private Json() {
    }

    // ---------------------------------------------------------------- escrita

    static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escreverValor(sb, valor);
        return sb.toString();
    }

    private static void escreverValor(StringBuilder sb, Object valor) {
        if (valor == null) {
            sb.append("null");
        } else if (valor instanceof String s) {
            escreverTexto(sb, s);
        } else if (valor instanceof Number || valor instanceof Boolean) {
            sb.append(valor);
        } else if (valor instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                escreverTexto(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escreverValor(sb, e.getValue());
            }
            sb.append('}');
        } else if (valor instanceof Iterable<?> itens) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : itens) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                escreverValor(sb, item);
            }
            sb.append(']');
        } else {
            escreverTexto(sb, String.valueOf(valor));
        }
    }

    private static void escreverTexto(StringBuilder sb, String texto) {
        sb.append('"');
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- leitura

    @SuppressWarnings("unchecked")
    static Map<String, Object> lerObjeto(String texto) throws IOException {
        Object valor = ler(texto);
        if (!(valor instanceof Map)) {
            throw new IOException("esperado um objeto JSON");
        }
        return (Map<String, Object>) valor;
    }

    static List<Object> lerLista(String texto) throws IOException {
        Object valor = ler(texto);
        if (!(valor instanceof List)) {
            throw new IOException("esperado um array JSON");
        }
        return (List<Object>) valor;
    }

    static Object ler(String texto) throws IOException {
        if (texto == null) {
            throw new IOException("conteudo vazio");
        }
        Leitor leitor = new Leitor(texto);
        Object valor = leitor.valor();
        leitor.pularEspacos();
        if (!leitor.fim()) {
            throw new IOException("conteudo extra depois do valor JSON, posicao " + leitor.pos);
        }
        return valor;
    }

    /** Descida recursiva simples. Sem streaming: as mensagens do protocolo cabem folgadas em memoria. */
    private static final class Leitor {

        private final String s;
        private int pos;

        Leitor(String s) {
            this.s = s;
        }

        boolean fim() {
            return pos >= s.length();
        }

        void pularEspacos() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object valor() throws IOException {
            pularEspacos();
            if (fim()) {
                throw new IOException("fim inesperado do JSON");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> objeto();
                case '[' -> lista();
                case '"' -> texto();
                case 't', 'f' -> booleano();
                case 'n' -> nulo();
                default -> numero();
            };
        }

        private Map<String, Object> objeto() throws IOException {
            Map<String, Object> mapa = new LinkedHashMap<>();
            pos++; // {
            pularEspacos();
            if (!fim() && s.charAt(pos) == '}') {
                pos++;
                return mapa;
            }
            while (true) {
                pularEspacos();
                if (fim() || s.charAt(pos) != '"') {
                    throw new IOException("esperada chave entre aspas na posicao " + pos);
                }
                String chave = texto();
                pularEspacos();
                exigir(':');
                mapa.put(chave, valor());
                pularEspacos();
                if (fim()) {
                    throw new IOException("objeto JSON nao fechado");
                }
                char c = s.charAt(pos++);
                if (c == '}') {
                    return mapa;
                }
                if (c != ',') {
                    throw new IOException("esperado ',' ou '}' na posicao " + (pos - 1));
                }
            }
        }

        private List<Object> lista() throws IOException {
            List<Object> itens = new ArrayList<>();
            pos++; // [
            pularEspacos();
            if (!fim() && s.charAt(pos) == ']') {
                pos++;
                return itens;
            }
            while (true) {
                itens.add(valor());
                pularEspacos();
                if (fim()) {
                    throw new IOException("array JSON nao fechado");
                }
                char c = s.charAt(pos++);
                if (c == ']') {
                    return itens;
                }
                if (c != ',') {
                    throw new IOException("esperado ',' ou ']' na posicao " + (pos - 1));
                }
            }
        }

        private String texto() throws IOException {
            pos++; // aspas de abertura
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (fim()) {
                    throw new IOException("string JSON nao fechada");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (fim()) {
                    throw new IOException("escape incompleto no fim da string");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw new IOException("escape unicode incompleto");
                        }
                        String hex = s.substring(pos, pos + 4);
                        pos += 4;
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException nfe) {
                            throw new IOException("escape unicode invalido: \\u" + hex);
                        }
                    }
                    default -> throw new IOException("escape desconhecido: \\" + e);
                }
            }
        }

        private Object booleano() throws IOException {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IOException("valor invalido na posicao " + pos);
        }

        private Object nulo() throws IOException {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IOException("valor invalido na posicao " + pos);
        }

        private Object numero() throws IOException {
            int inicio = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            String bruto = s.substring(inicio, pos);
            if (bruto.isEmpty()) {
                throw new IOException("valor invalido na posicao " + inicio);
            }
            try {
                if (bruto.contains(".") || bruto.contains("e") || bruto.contains("E")) {
                    return Double.parseDouble(bruto);
                }
                return Long.parseLong(bruto);
            } catch (NumberFormatException e) {
                throw new IOException("numero invalido: " + bruto);
            }
        }

        private void exigir(char esperado) throws IOException {
            if (fim() || s.charAt(pos) != esperado) {
                throw new IOException("esperado '" + esperado + "' na posicao " + pos);
            }
            pos++;
        }
    }
}
